package optional.examples

import scala.util.matching.Regex
import java.io.File

object sgrep extends optional.Application
{
  def mkRegexp(s: String): Regex = s.r
  
/** TODO fix me, this is --v, should be -v */
  def main(v: Boolean, i: Boolean, arg1: Regex, arg2: File) {
    // reverse condition if -v is given
    def cond(x: Option[_]) = if (v) x.isEmpty else x.isDefined
    // case insensitive if -i is given
    val regex = if (i) ("""(?i)""" + arg1.toString).r else arg1

    for (line <- io.Source.fromFile(arg2).getLines()
         ; if cond(regex findFirstIn line))
      print(line)
  }
}
