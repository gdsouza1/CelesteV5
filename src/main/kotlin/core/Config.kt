package core

val prefix: String by lazy {
    ConfigurationAccessor.getConfig("PREFIX")
}

const val shouldAttentBots = false