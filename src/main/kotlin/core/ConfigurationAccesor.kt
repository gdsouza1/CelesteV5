package core

import javax.naming.ServiceUnavailableException

object ConfigurationAccessor {

    private val herokuPath = "C:\\Program Files\\heroku\\bin\\heroku.cmd"

    fun getConfig(key: String): String {
        return tryEnv(key) ?: getRemote(key) ?: throw ServiceUnavailableException("Cannot retrieve config var")
    }

    //Will only work while running in heroku
    private fun tryEnv(key: String): String? {
        val value: String? = System.getenv(key)
        return value
    }

    //Secondary option for retrieving remotely
    private fun getRemote(key: String): String? {
        val runtime = Runtime.getRuntime()
        val process = runtime.exec("$herokuPath config:get $key -a celestev5")
        val stream = process.inputStream
        val reader = stream.bufferedReader()
        return reader.readLine()
    }

}