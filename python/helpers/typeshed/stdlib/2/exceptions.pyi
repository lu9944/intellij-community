from typing import Any, Tuple, Optional

class StandardError(Exception): ...
class ArithmeticError(StandardError): ...
class AssertionError(StandardError): ...
class AttributeError(StandardError): ...
class BaseException(object):
    args = ...  # type: Tuple[Any, ...]
    message = ...  # type: str
    def __getslice__(self, start, end) -> Any: ...
    def __getitem__(self, start, end) -> Any: ...
    def __unicode__(self) -> unicode: ...
class BufferError(StandardError): ...
class BytesWarning(Warning): ...
class DeprecationWarning(Warning): ...
class EOFError(StandardError): ...class EnvironmentError(StandardError):
    errno = ...  # type: int
    strerror = ...  # type: str
    filename = ...  # type: str
class Exception(BaseException): ...
class FloatingPointError(ArithmeticError): ...
class FutureWarning(Warning): ...
class GeneratorExit(BaseException): ...
class IOError(EnvironmentError): ...
class ImportError(StandardError): ...
class ImportWarning(Warning): ...
class IndentationError(SyntaxError): ...
class IndexError(LookupError): ...
class KeyError(LookupError): ...
class KeyboardInterrupt(BaseException): ...
class LookupError(StandardError): ...
class MemoryError(StandardError): ...
class NameError(StandardError): ...
class NotImplementedError(RuntimeError): ...
class OSError(EnvironmentError): ...
class OverflowError(ArithmeticError): ...
class PendingDeprecationWarning(Warning): ...
class ReferenceError(StandardError): ...
class RuntimeError(StandardError): ...
class RuntimeWarning(Warning): ...
class StopIteration(Exception): ...
class SyntaxError(StandardError):
    text = ...  # type: str
    print_file_and_line = ...  # type: Optional[str]
    filename = ...  # type: str
    lineno = ...  # type: int
    offset = ...  # type: int
    msg = ...  # type: str
class SyntaxWarning(Warning): ...
class SystemError(StandardError): ...
class SystemExit(BaseException):
    code = ...  # type: int
class TabError(IndentationError): ...
class TypeError(StandardError): ...
class UnboundLocalError(NameError): ...
class UnicodeError(ValueError): ...
class UnicodeDecodeError(UnicodeError):
    start = ...  # type: int
    reason = ...  # type: str
    object = ...  # type: str
    end = ...  # type: int
    encoding = ...  # type: str
class UnicodeEncodeError(UnicodeError):
    start = ...  # type: int
    reason = ...  # type: str
    object = ...  # type: unicode
    end = ...  # type: int
    encoding = ...  # type: str
class UnicodeTranslateError(UnicodeError):
    start = ...  # type: int
    reason = ...  # type: str
    object = ...  # type: Any
    end = ...  # type: int
    encoding = ...  # type: str
class UnicodeWarning(Warning): ...
class UserWarning(Warning): ...
class ValueError(StandardError): ...
class Warning(Exception): ...
class ZeroDivisionError(ArithmeticError): ...
