/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.extractMethod.InputVariables;
import com.intellij.refactoring.util.duplicates.DuplicatesFinder;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.refactoring.util.duplicates.ReturnValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;

/**
 * @author yole, Bas Leijdekkers
 */
public class TryWithIdenticalCatchesInspection extends BaseInspection {

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    return InspectionGadgetsBundle.message("try.with.identical.catches.problem.descriptor", type.getPresentableText());
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("try.with.identical.catches.display.name");
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return JavaFeature.MULTI_CATCH.isFeatureSupported(file);
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    if (element instanceof PsiCatchSection) {
      final PsiCatchSection catchSection = (PsiCatchSection)element;
      final PsiParameter parameter = catchSection.getParameter();
      if (parameter != null && super.isSuppressedFor(parameter)) {
        return true;
      }
    }
    return super.isSuppressedFor(element);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TryWithIdenticalCatchesVisitor();
  }

  private static class TryWithIdenticalCatchesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTryStatement(PsiTryStatement statement) {
      super.visitTryStatement(statement);

      final CatchSectionWrapper[] sections = CatchSectionWrapper.createWrappers(statement);
      if (sections == null) return;

      final boolean[][] canSwap = collectCanSwap(sections);
      final CatchSectionIndices[] duplicateIndices = getCatchSectionIndices(sections, canSwap, CatchSectionWrapper::areDuplicates);
      final CatchSectionIndices[] emptyIndices = isOnTheFly() ? getCatchSectionIndices(sections, canSwap, CatchSectionWrapper::areEmpty) : null;
      if (duplicateIndices == null && emptyIndices == null) return;

      final boolean[] problems = new boolean[sections.length];
      registerProblems(sections, duplicateIndices, problems, false);
      registerProblems(sections, emptyIndices, problems, true);
    }

    private void registerProblems(@NotNull CatchSectionWrapper[] sections,
                                  @Nullable CatchSectionIndices[] sectionIndices,
                                  @NotNull boolean[] problems,
                                  boolean empty) {
      if (sectionIndices == null) return;

      for (int index = 0; index < sections.length; index++) {
        if (problems[index]) continue;

        int collapseIntoIndex = sectionIndices[index].myCollapseIntoIndex;
        if (collapseIntoIndex >= 0) {
          registerProblem(sections, index, collapseIntoIndex, empty);
          problems[index] = true;
        }
      }
    }

    private void registerProblem(@NotNull CatchSectionWrapper[] sections, int at, int collapseIntoIndex, boolean empty) {
      final PsiCatchSection section = sections[at].myCatchSection;
      final PsiJavaToken rParenth = section.getRParenth();
      if (rParenth != null) {
        registerErrorAtOffset(section, 0, rParenth.getStartOffsetInParent() + 1,
                              empty ? ProblemHighlightType.INFORMATION : ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                              sections[collapseIntoIndex].myParameter.getType(), empty);
      }
    }
  }

  @Nullable
  static CatchSectionIndices[] getCatchSectionIndices(@NotNull CatchSectionWrapper[] sections,
                                                      @NotNull boolean[][] canSwap,
                                                      @NotNull BiPredicate<? super CatchSectionWrapper, ? super CatchSectionWrapper> equals) {
    final CatchSectionIndices[] indices = new CatchSectionIndices[sections.length];
    for (int index = 0; index < sections.length; index++) {
      indices[index] = new CatchSectionIndices(index);
    }

    boolean duplicateFound = false;
    for (int from = 0; from < sections.length - 1; from++) {
      if (indices[from].myHasDuplicate) continue;
      final CatchSectionWrapper section = sections[from];
      if (section == null) continue;

      for (int to = from + 1; to < sections.length; to++) {
        if (indices[to].myHasDuplicate) continue;
        final CatchSectionWrapper otherSection = sections[to];
        if (otherSection == null || !equals.test(section, otherSection)) continue;

        indices[from].addDuplicate(indices[to]);
        duplicateFound = true;
      }
    }
    if (!duplicateFound) return null;

    for (int index = 0; index < sections.length; index++) {
      indices[index].computeInsertionRange(canSwap);
    }

    for (CatchSectionIndices idx : indices) {
      final int[] duplicates = idx.myDuplicates;
      if (duplicates == null) continue;

      for (int from : duplicates) {
        for (int to : duplicates) {
          indices[to].tryCollapseInto(indices[from]);
        }
      }
    }
    return indices;
  }

  private static boolean[][] collectCanSwap(@NotNull CatchSectionWrapper[] sections) {
    final boolean[][] canSwap = new boolean[sections.length][sections.length];
    for (int from = 0; from < sections.length; from++) {
      for (int to = from + 1; to < sections.length; to++) {
        canSwap[from][to] = canSwap[to][from] = sections[from] != null && sections[from].canSwapWith(sections[to]);
      }
    }
    return canSwap;
  }

  private static class CatchSectionIndices {
    final int myIndex;
    int myCanInsertBefore = -1;
    int myCanInsertAfter = -1;

    boolean myHasDuplicate;
    int[] myDuplicates;
    int myCollapseIntoIndex = -1;

    CatchSectionIndices(int index) {
      myIndex = index;
    }

    void addDuplicate(CatchSectionIndices duplicate) {
      if (myDuplicates == null) {
        myDuplicates = new int[]{myIndex, duplicate.myIndex};
        myHasDuplicate = true;
      }
      else {
        myDuplicates = ArrayUtil.append(myDuplicates, duplicate.myIndex);
      }
      duplicate.myHasDuplicate = true;
    }

    void computeInsertionRange(@NotNull boolean[][] canSwap) {
      boolean[] canSwapWith = canSwap[myIndex];

      for (int before = myIndex; ; before--) {
        if (before - 1 < 0 || !canSwapWith[before - 1]) {
          myCanInsertBefore = before;
          break;
        }
      }

      for (int after = myIndex; ; after++) {
        if (after + 1 >= canSwapWith.length || !canSwapWith[after + 1]) {
          myCanInsertAfter = after;
          break;
        }
      }
    }

    public void tryCollapseInto(CatchSectionIndices collapseInto) {
      if (myCollapseIntoIndex < 0 && myIndex > collapseInto.myIndex && myCanInsertBefore <= collapseInto.myCanInsertAfter + 1) {
        myCollapseIntoIndex = collapseInto.myIndex;
      }
    }
  }

  private static class CatchSectionWrapper {
    @NotNull final PsiCatchSection myCatchSection;
    @NotNull final PsiCodeBlock myCodeBlock;
    @NotNull final PsiParameter myParameter;
    @NotNull final List<? extends PsiClassType> myTypes;
    @NotNull final DuplicatesFinder myFinder;

    private CatchSectionWrapper(@NotNull PsiCatchSection catchSection,
                                @NotNull PsiCodeBlock codeBlock,
                                @NotNull PsiParameter parameter,
                                @NotNull List<? extends PsiClassType> types,
                                @NotNull DuplicatesFinder finder) {
      myCatchSection = catchSection;
      myCodeBlock = codeBlock;
      myParameter = parameter;
      myTypes = types;
      myFinder = finder;
    }

    static boolean areEmpty(@NotNull CatchSectionWrapper s1, @NotNull CatchSectionWrapper s2) {
      return s1.myCodeBlock.isEmpty() && s2.myCodeBlock.isEmpty();
    }

    static boolean areDuplicates(@NotNull CatchSectionWrapper s1, @NotNull CatchSectionWrapper s2) {
      final boolean empty1 = s1.myCodeBlock.isEmpty();
      final boolean empty2 = s2.myCodeBlock.isEmpty();
      if (empty1 != empty2) return false;

      if (empty1) {
        final List<String> comments1 = collectCommentTexts(s1.myCatchSection);
        final List<String> comments2 = collectCommentTexts(s2.myCatchSection);
        return comments1.equals(comments2);
      }

      final Match match1 = s1.findDuplicate(s2);
      if (match1 == null) {
        return false;
      }
      final Match match2 = s2.findDuplicate(s1);
      if (match2 == null) {
        return false;
      }
      final ReturnValue returnValue1 = match1.getReturnValue();
      final ReturnValue returnValue2 = match2.getReturnValue();
      if (returnValue1 == null) {
        return returnValue2 == null;
      }
      return returnValue1.isEquivalent(returnValue2);
    }

    private Match findDuplicate(@NotNull CatchSectionWrapper section) {
      return myFinder.isDuplicate(section.myCodeBlock, true);
    }

    boolean canSwapWith(@Nullable CatchSectionWrapper section) {
      if (section == null) return false;
      for (PsiClassType type : myTypes) {
        for (PsiClassType otherType : section.myTypes) {
          if (type.isAssignableFrom(otherType) || otherType.isAssignableFrom(type)) {
            return false;
          }
        }
      }
      return true;
    }

    @Nullable
    static CatchSectionWrapper[] createWrappers(@NotNull PsiTryStatement statement) {
      final PsiCatchSection[] catchSections = statement.getCatchSections();
      if (catchSections.length < 2) {
        return null;
      }
      final PsiParameter[] parameters = statement.getCatchBlockParameters();
      if (catchSections.length != parameters.length) {
        return null;
      }
      final CatchSectionWrapper[] sections = new CatchSectionWrapper[catchSections.length];
      for (int i = 0; i < sections.length; i++) {
        sections[i] = createWrapper(catchSections[i]);
      }
      return sections;
    }

    @Nullable
    private static CatchSectionWrapper createWrapper(@NotNull PsiCatchSection catchSection) {
      final PsiParameter parameter = catchSection.getParameter();
      final PsiCodeBlock codeBlock = catchSection.getCatchBlock();
      if (parameter != null && codeBlock != null) {
        final List<PsiClassType> types = getClassTypes(parameter.getType());
        if (types != null) {
          final DuplicatesFinder finder = buildDuplicatesFinder(codeBlock, parameter);
          return new CatchSectionWrapper(catchSection, codeBlock, parameter, types, finder);
        }
      }
      return null;
    }

    @Nullable
    private static List<PsiClassType> getClassTypes(@Nullable PsiType type) {
      if (type instanceof PsiClassType) {
        return Collections.singletonList((PsiClassType)type);
      }
      if (type instanceof PsiDisjunctionType) {
        final List<PsiType> disjunctions = ((PsiDisjunctionType)type).getDisjunctions();
        if (!disjunctions.isEmpty()) {
          final List<PsiClassType> classTypes = ContainerUtil.mapNotNull(disjunctions, t -> ObjectUtils.tryCast(t, PsiClassType.class));
          if (classTypes.size() == disjunctions.size()) {
            return classTypes;
          }
        }
      }
      return null;
    }

    @NotNull
    private static DuplicatesFinder buildDuplicatesFinder(@NotNull PsiCodeBlock catchBlock, @NotNull PsiParameter parameter) {
      final InputVariables inputVariables =
        new InputVariables(Collections.singletonList(parameter), parameter.getProject(), new LocalSearchScope(catchBlock), false);
      return new DuplicatesFinder(new PsiElement[]{catchBlock}, inputVariables, null, Collections.emptyList());
    }
  }

  @NotNull
  private static List<String> collectCommentTexts(@NotNull PsiElement element) {
    final List<String> result = new ArrayList<>();
    PsiTreeUtil.processElements(element, child -> {
      if (child instanceof PsiComment) {
        String text = getCommentText((PsiComment)child);
        if (!text.isEmpty()) {
          result.add(text);
        }
      }
      return true;
    });
    return result;
  }

  @NotNull
  private static String getCommentText(@NotNull PsiComment comment) {
    final IElementType type = comment.getTokenType();
    if (type == JavaTokenType.END_OF_LINE_COMMENT) {
      return StringUtil.trimStart(comment.getText(), "//").trim();
    }
    if (type == JavaTokenType.C_STYLE_COMMENT) {
      return StringUtil.trimStart(StringUtil.trimEnd(comment.getText(), "*/"), "/*").trim();
    }
    return "";
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new CollapseCatchSectionsFix((Boolean)infos[1]);
  }

  private static class CollapseCatchSectionsFix extends InspectionGadgetsFix {
    private final boolean myEmpty;

    CollapseCatchSectionsFix(boolean empty) {
      myEmpty = empty;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("try.with.identical.catches.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      // smart psi pointer lost correct catch section when multiple catch sections were collapsed in batch mode
      // so we need to re-calculate everything based on what exists at this point
      final PsiCatchSection catchSection = (PsiCatchSection)descriptor.getPsiElement();
      final PsiTryStatement tryStatement = (PsiTryStatement)catchSection.getParent();

      final CatchSectionWrapper[] sections = CatchSectionWrapper.createWrappers(tryStatement);
      if (sections == null) return;

      int sectionIndex = getSectionIndex(sections, catchSection);
      if (sectionIndex < 0) return;

      CatchSectionWrapper duplicateSection = sections[sectionIndex];
      if (duplicateSection == null) return;

      final boolean[][] canSwap = collectCanSwap(sections);
      final CatchSectionIndices[] duplicatesIndices =
        getCatchSectionIndices(sections, canSwap, myEmpty ? CatchSectionWrapper::areEmpty : CatchSectionWrapper::areDuplicates);
      if (duplicatesIndices == null) return;

      final int collapseIntoIndex = duplicatesIndices[sectionIndex].myCollapseIntoIndex;
      if (collapseIntoIndex < 0) return;

      final CatchSectionWrapper collapseIntoSection = sections[collapseIntoIndex];
      if (collapseIntoSection == null) return;

      final PsiTypeElement collapseIntoTypeElement = collapseIntoSection.myParameter.getTypeElement();
      if (collapseIntoTypeElement == null) return;

      final Set<String> survivingCommentTexts = new HashSet<>(collectCommentTexts(collapseIntoSection.myCatchSection));
      final List<PsiType> parameterTypes = new ArrayList<>(collapseIntoSection.myTypes);
      parameterTypes.addAll(duplicateSection.myTypes);

      final List<PsiType> filteredTypes = PsiDisjunctionType.flattenAndRemoveDuplicates(parameterTypes);
      final PsiType disjunction = PsiDisjunctionType.createDisjunction(filteredTypes, tryStatement.getManager());
      final PsiTypeElement newTypeElement = JavaPsiFacade.getElementFactory(project).createTypeElement(disjunction);

      JavaCodeStyleManager.getInstance(project).shortenClassReferences(collapseIntoTypeElement.replace(newTypeElement));

      int insertBeforeIndex = duplicatesIndices[sectionIndex].myCanInsertBefore;
      if (collapseIntoIndex < insertBeforeIndex) {
        // We can't leave the merged 'catch' section at collapseIntoIndex because it conflicts with other caught exceptions
        final PsiCatchSection[] catchSections = tryStatement.getCatchSections();
        if (insertBeforeIndex < catchSections.length && catchSections[insertBeforeIndex] != null) {
          tryStatement.addBefore(collapseIntoSection.myCatchSection, catchSections[insertBeforeIndex]);
          collapseIntoSection.myCatchSection.delete();
        }
      }

      final CommentTracker tracker = new CommentTracker();
      PsiTreeUtil.processElements(duplicateSection.myCatchSection, element -> {
        if (element instanceof PsiComment) {
          final String text = getCommentText((PsiComment)element);
          if (text.isEmpty() || survivingCommentTexts.contains(text)) {
            tracker.markUnchanged(element);
          }
        }
        return true;
      });
      tracker.deleteAndRestoreComments(duplicateSection.myCatchSection);
    }

    private static int getSectionIndex(@NotNull CatchSectionWrapper[] sections, @NotNull PsiElement catchSection) {
      for (int i = 0; i < sections.length; i++) {
        if (sections[i].myCatchSection == catchSection) {
          return i;
        }
      }
      return -1;
    }
  }
}
