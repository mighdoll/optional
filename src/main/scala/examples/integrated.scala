package optional.examples
import optional.{Help,Alias}

object Integrated extends optional.Application {
  def main(
        @Help("show version") @Alias("v") 
      version: Boolean,
        @Help("status of system") @Alias("s") 
      status: Boolean,
      file:String,
      arg1:Int) {
    if (version) { Console println "version 0.1" }
    if (status) { Console println "status" }
  }
}

