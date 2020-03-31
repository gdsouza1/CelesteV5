package core

import com.google.common.collect.ImmutableSet
import kotlinx.coroutines.ObsoleteCoroutinesApi
import net.dv8tion.jda.api.AccountType
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import org.apache.log4j.Logger
import com.google.common.reflect.ClassPath
import javax.security.auth.login.LoginException


@ExperimentalStdlibApi
@ObsoleteCoroutinesApi
object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        try {


            val token = ConfigurationAccessor.getConfig("TOKEN_V3")
            val jdab = JDABuilder(AccountType.BOT)

            jdab.setToken(token)
            //Start invisible
            jdab.setStatus(OnlineStatus.INVISIBLE)
            //Set Listener to core producer Listener.kt
            jdab.addEventListeners(Listener)
            //Builds and awaits completion
            jdab.build().awaitReady()

            StartupRoutine.start()
        } catch (loginEx: LoginException) {
            Logger.getRootLogger().error("Failed to login", loginEx)
        }
    }
}



