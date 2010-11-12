package optional.examples
import optional.{Help,Alias}

object Util {
  implicit def optionToRichOption[T](opt:Option[T]) = new RichOption(opt)

  class RichOption[T](opt:Option[T]) {
    def ifDefined[R](fn: =>R):Option[R] = {
      if (opt.isDefined)
        Some(fn)
      else
        None
    }
  }
}
import Util._

object Integrated extends optional.Application {
  def main(
      @Help("show version") @Alias("v") 
    version: Option[Boolean],
      @Help("status of system") @Alias("s") 
    status: Option[Boolean],
    file:String,
    arg1:Int
  ) {
    version ifDefined { Console println "version 0.1" }
    if (status.isDefined) { Console println "status" }
  }
}

