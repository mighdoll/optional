package optional

import scala.collection._
import mutable.HashSet
import Application._


case class Options(
  options: Map[String, String],
  args: List[String],
  rawArgs: List[String]
)

object Options
{
  private val ShortOption = """-(\w)""".r
  private val ShortSquashedOption = """-([^-\s]\w+)""".r
  private val LongOption = """--(\w+)""".r
  private val OptionTerminator = "--"
  private val True = "true";

  /**
   * Take a list of string arguments and parse them into options.
   * Currently the dumbest option parser in the entire world, but
   * oh well.
   */
  def parse(argInfos: Iterable[MainArg], args: String*): Options = {
    import mutable._;
    val optionsStack = new ArrayStack[String];
    val options = new OpenHashMap[String, String];
    val arguments = new ArrayBuffer[String];
    
    def longArg(name: String):Option[MainArg] = {
      argInfos find {
        case m:MainArg if m.name == name  => true
        case _                            => false
      }
    }

    def shortArg(c:Char):Option[MainArg] = {
      argInfos find {
        case m:MainArg if m.alias == c  => true
        case _                          => false
      }
    }

    def addOption(arg:MainArg) {
      if (arg.isSwitch) {
        options(arg.name) = True
      } else if (optionsStack.isEmpty) {
        usageError("missing parameter for: %s" format(arg.name)) 
      } else {
        val next = optionsStack.pop;
        next match {
          case ShortOption(_) | ShortSquashedOption(_) | LongOption(_) | OptionTerminator =>
            usageError("missing parameter for: %s" format(arg.name)) 
          case x => options(arg.name) = x;
        }
      }
    }

    def addShortOption(c:Char) {
      val info = shortArg(c) getOrElse usageError("unrecognized option: -%c" format(c)) 
      addOption(info)
    }

    def addLongOption(name: String) {
      val info = longArg(name) getOrElse usageError("unrecognized option: --%s" format(name))
      addOption(info)
    }

    optionsStack ++= args.reverse;    
    while(!optionsStack.isEmpty){
      optionsStack.pop match {
        case ShortSquashedOption(xs) => xs foreach addShortOption
        case ShortOption(name) => addShortOption(name(0))
        case LongOption(name) => addLongOption(name);
        case OptionTerminator => optionsStack.drain(arguments += _);
        case x => arguments += x; 
      }
    }

    Options(options, arguments.toList, args.toList)
  }
}
