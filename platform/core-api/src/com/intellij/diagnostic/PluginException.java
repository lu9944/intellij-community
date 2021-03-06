/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.diagnostic;

import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents an internal error caused by a plugin. It may happen if the plugin's code fails with an exception, or if the plugin violates
 * some contract of IntelliJ Platform. If such exceptions are reported to JetBrains they may be automatically attributed to corresponding
 * plugins.
 *
 * @see com.intellij.ide.plugins.PluginManagerCore#createPluginException
 */
public class PluginException extends RuntimeException {
  private final PluginId myPluginId;

  public PluginException(@NotNull String message, Throwable cause, @Nullable PluginId pluginId) {
    super(message, cause);
    myPluginId = pluginId;
  }

  public PluginException(@NotNull Throwable e, @Nullable PluginId pluginId) {
    super (e.getMessage(), e);
    myPluginId = pluginId;
  }

  public PluginException(@NotNull String message, @Nullable PluginId pluginId) {
    super(message);
    myPluginId = pluginId;
  }

  @Nullable
  public PluginId getPluginId() {
    return myPluginId;
  }

  @Override
  @NotNull 
  public String getMessage() {
    String message = super.getMessage();
    return myPluginId != null ? StringUtil.notNullize(message) + " [Plugin: " + myPluginId + "]" : message;
  }
}
