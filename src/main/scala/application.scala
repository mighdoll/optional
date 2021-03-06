package optional

import com.thoughtworks.paranamer.BytecodeReadingParanamer
import java.io.File.separator
import java.{ lang => jl }
import java.lang.{ Class => JClass }
import jl.reflect.{ Array => _, _ }
import collection.mutable.HashSet
import java.lang.annotation.Annotation

case class DesignError(msg: String) extends Error(msg)
case class UsageError(msg: String) extends RuntimeException(msg)

object Util
{
  val CString       = classOf[String]
  val CInteger      = classOf[jl.Integer]
  val CBool         = classOf[scala.Boolean]
  val CBoolean      = classOf[jl.Boolean]
  val CArrayString  = classOf[Array[String]]
  
  val ArgArgument = """^arg(\d+)$""".r
  
  def cond[T](x: T)(f: PartialFunction[T, Boolean]) =
    (f isDefinedAt x) && f(x)
  def condOpt[T,U](x: T)(f: PartialFunction[T, U]): Option[U] =
    if (f isDefinedAt x) Some(f(x)) else None
  
  def stringForType(tpe: Type): String =
    if (tpe == classOf[Int] || tpe == classOf[jl.Integer]) "Int"
    else if (tpe == classOf[Long] || tpe == classOf[jl.Long]) "Long"
    else if (tpe == classOf[Short] || tpe == classOf[jl.Short]) "Short"
    else if (tpe == classOf[Byte] || tpe == classOf[jl.Byte]) "Byte"
    else if (tpe == classOf[Float] || tpe == classOf[jl.Float]) "Float"
    else if (tpe == classOf[Double] || tpe == classOf[jl.Double]) "Double"
    else if (tpe == classOf[Char] || tpe == classOf[jl.Character]) "Char"
    else if (tpe == CString) "String"
    else tpe match {
      case x: Class[_]  => x.getName()
      case x            => x.toString()
    }  

  /** Find an element for which a partial function is defined.  Return the partial function
   *  result wrapped in a Some(), or else return None if the partial function is not defined 
   *  for any elements.
   */
  def findMap[T,R](coll:Traversable[T], fn:PartialFunction[T,R]):Option[R] = {
    coll find {elem => fn.isDefinedAt(elem)} map {elem => fn(elem)}
  }
  
  /** maps to Some(value) where pf is defined, else to none */
  def partialMap[T,R](coll:Traversable[T], fn:PartialFunction[T,R]):Traversable[Option[R]] = {
    for (elem <- coll) yield {
      if (fn.isDefinedAt(elem)) Some(fn(elem))
      else None
    } 
  }
}

import Util._

private object OptionType {
  def unapply(x: Any) = condOpt(x) {
    case x: ParameterizedType if x.getRawType == classOf[Option[_]] => x.getActualTypeArguments()(0)
  }
}

object Argument {
  def apply(name: String, tpe: Type, alias:Option[Char], help:Option[String]): Argument = tpe match {
    case CBool | CBoolean => BoolArg(name, alias, help)
    case OptionType(t)  => OptArg(name, t, tpe, alias, help)
    case _              =>
      name match {      
        case ArgArgument(num)  => PosArg(name, tpe, num.toInt, alias, help)
        case _              => ReqArg(name, tpe, alias, help)
      }
    }

  def unapply(x: Any): Option[(String, Type, Type)] = x match {
    case OptArg(name, tpe, originalType, _, _)  => Some(name, tpe, originalType)
    case BoolArg(name, _, _)                    => Some(name, CBoolean, CBoolean)
    case ReqArg(name, tpe, _, _)                => Some(name, tpe, tpe)
    case PosArg(name, tpe, num, _, _)           => Some(name, tpe, tpe)
  }
}

sealed abstract class Argument {
  def name: String
  def tpe: Type
  def originalType: Type
  def isOptional: Boolean
  def usage: String
  def alias:Option[Char]
  def help:Option[String]

  def pos: Int = -1
  def isPositional = pos > -1
  def isSwitch = false

  def mkUsage(base:String) = "%15s  %s" format(base, help)
}

case class OptArg(name: String, tpe: Type, originalType: Type, alias:Option[Char], help:Option[String]) 
    extends Argument {
  val isOptional = true
  def usage = "[--%s %s]".format(name, stringForType(tpe))
}
case class ReqArg(name: String, tpe: Type, alias:Option[Char], help:Option[String]) extends Argument {
  val originalType = tpe
  val isOptional = false
  def usage = "<%s: %s> ".format(name, stringForType(tpe))
}
case class PosArg(name: String, tpe: Type, override val pos: Int, alias:Option[Char], help:Option[String]) 
    extends Argument {
  val originalType = tpe
  val isOptional = false
  def usage = "<%s>".format(stringForType(tpe))
}
case class BoolArg(name: String, alias:Option[Char], help:Option[String]) extends Argument {
  val tpe, originalType = CBoolean
  val isOptional = true
  override def isSwitch = true
  def usage = "[--%s]".format(name)
}

object Application {
  def usageError(msg: String) = throw UsageError(msg)
}
import Application._

/**
 *  This trait automagically finds a main method on the object 
 *  which mixes this in and based on method names and types figures
 *  out the options it should be called with and takes care of parameter parsing
 */ 
trait Application {
  /** Public methods.  */
  def getRawArgs()  = opts.rawArgs
  def getArgs()     = opts.args
  
  /** These methods can be overridden to modify application behavior.  */

  /** Override this if you want to restrict the search space of conversion methods. */
  protected def isConversionMethod(m: Method) = true
  
  /** The autogenerated usage message will usually suffice. */
  protected def programName   = "program"
  protected def usageMessage  = "Usage: %s %s".format(programName, mainArgs map (_.usage) mkString " ")
  
  /** If you mess with anything from here on down, you're on your own.  */
  
  private def methods(f: Method => Boolean): List[Method] = getClass.getMethods.toList filter f
  private def signature(m: Method) = m.toGenericString.replaceAll("""\S+\.main\(""", "main(") // ))
  private def designError(msg: String) = throw DesignError(msg)

  private def isRealMain(m: Method)     = cond(m.getParameterTypes) { case Array(CArrayString) => true }
  private def isEligibleMain(m: Method) = m.getName == "main" && !isRealMain(m)
  lazy val mainMethod = methods(isEligibleMain) match {
    case Nil      => designError("No eligible main method found")
    case List(x)  => x
    case xs       =>
      designError("You seem to have multiple main methods, signatures:\n%s" .
        format(xs map signature mkString "\n")
      )
  }
  
  private lazy val mainAnnotations = mainMethod.getParameterAnnotations 
  private lazy val help = mainAnnotations map { 
      findMap[Annotation,String](_, { case help:Help => help.value}) 
    }
  private lazy val aliases = mainAnnotations map { 
      findMap[Annotation,Char](_, { case al:Alias => new String(al.value)(0)}) 
    } 

  private lazy val parameterTypes   = mainMethod.getGenericParameterTypes.toList
  private lazy val argumentNames    = (new BytecodeReadingParanamer lookupParameterNames mainMethod map (_.replaceAll("\\$.+", ""))).toList
  private lazy val mainArgs         = {
      val typesIter = parameterTypes.iterator
      val aliasesIter = aliases.iterator
      val helpIter = help.iterator
      argumentNames map {name =>
        Argument(name, typesIter.next, aliasesIter.next, helpIter.next)
      }
    }
  private lazy val reqArgs          = mainArgs filter (x => !x.isOptional)
  private def posArgCount           = mainArgs filter (_.isPositional) size

  def getAnyValBoxedClass(x: JClass[_]): JClass[_] =
    if (x == classOf[Byte]) classOf[jl.Byte]
    else if (x == classOf[Short]) classOf[jl.Short]
    else if (x == classOf[Int]) classOf[jl.Integer]
    else if (x == classOf[Long]) classOf[jl.Long]
    else if (x == classOf[Float]) classOf[jl.Float]
    else if (x == classOf[Double]) classOf[jl.Double]
    else if (x == classOf[Char]) classOf[jl.Character]
    else if (x == classOf[Boolean]) classOf[jl.Boolean]
    else if (x == classOf[Unit]) classOf[Unit]
    else throw new Exception("Not an AnyVal: " + x)

  private val primitives = List(
    classOf[Byte], classOf[Short], classOf[Int], classOf[Long],
    classOf[Float], classOf[Double] // , classOf[Char], classOf[Boolean]
  )

  private val valueOfMap = {
    def m(clazz: JClass[_]) = getAnyValBoxedClass(clazz).getMethod("valueOf", CString)
    val xs1: List[(JClass[_], Method)] = primitives zip (primitives map m)
    val xs2: List[(JClass[_], Method)] = for (clazz <- (primitives map getAnyValBoxedClass)) yield (clazz, clazz.getMethod("valueOf", CString))

    Map[JClass[_], Method](xs1 ::: xs2 : _*)    
    // Map[JClass[_], Method](primitives zip (primitives map m) : _*)
  }
  
  def getConv(tpe: Type, value: String): Option[AnyRef] = {
    def isConv(m: Method) = isConversionMethod(m) && !(m.getName contains "$")

    methods(isConv) find (_.getGenericReturnType == tpe) map (_.invoke(this, value))
  }

  def getNumber(clazz: Class[_], value: String): Option[AnyRef] =
    try   { (valueOfMap get clazz) map (_.invoke(null, value)) }
    catch { case _: InvocationTargetException => None }

  /**
   * Magic method to take a string and turn it into something of a given type.
   */
  private def coerceTo(name: String, tpe: Type)(value: String): AnyRef = {
    def fail      = designError("Could not create type '%s' from String".format(tpe))
    def mismatch  = usageError("option --%s expects arg of type '%s' but was given '%s'".format(name, stringForType(tpe), value))
    def surprise  = usageError("Unexpected type: %s (%s)".format(tpe, tpe.getClass))
    
    tpe match {
      case CString          => value
      case CArrayString     => value split separator
      case OptionType(t)    => Some(coerceTo(name, t)(value))
      case clazz: Class[_]  => 
        if (valueOfMap contains clazz)
          getNumber(clazz, value) getOrElse mismatch
        else
          getConv(clazz, value) getOrElse {
            try   { clazz.getConstructor(CString).newInstance(value).asInstanceOf[AnyRef] }
            catch { case x: NoSuchMethodException => fail }
          }

      case x: ParameterizedType => getConv(x, value) getOrElse fail
      case x                    => surprise
    }
  }

  private var _opts: Options = null
  lazy val opts = _opts
  
  def callWithOptions(): Unit = {
    import opts._
    def missing(s: String)  = usageError("Missing required option '%s'".format(s))

    // verify minimum quantity of positional arguments
    if (args.size < posArgCount)
      usageError("too few arguments: expected %d, got %d".format(posArgCount, args.size))
    
    // verify all required options are present
    val missingArgs = reqArgs filter (x => !(options contains x.name) && !(x.name matches """^arg\d+$"""))
    if (!missingArgs.isEmpty) {
      val missingStr = missingArgs map ("--" + _.name) mkString " "        
      val s = if (missingArgs.size == 1) "" else "s"
      
      usageError("missing required option%s: %s".format(s, missingStr))
    }
    
    def determineValue(ma: Argument): AnyRef = {
      val Argument(name, _, tpe) = ma
      def isPresent = options contains name
      
      if (ma.isPositional)      coerceTo(name, tpe)(args(ma.pos - 1))
      else if (isPresent)       coerceTo(name, tpe)(options(name))
      else if (ma.isSwitch)     jl.Boolean.FALSE
      else if (ma.isOptional)   None
      else                      missing(name)
    }
    mainMethod.invoke(this, (mainArgs map determineValue).toArray : _*)
  }
  
  
  def main(cmdline: Array[String]) {
    try {
      _opts = Options.parse(mainArgs, cmdline: _*)
      callWithOptions()
    }
    catch {
      case UsageError(msg) =>
        println("Error: " + msg)
        println(usageMessage)
    }
  }
}
