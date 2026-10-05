package ru.colabike.app.push

import ru.rustore.sdk.pushclient.common.logger.Logger

/** The SDK's logger that says nothing: an address of a phone and a message do not go to the log. */
internal object SilentLogger : Logger {
    override fun verbose(message: String, throwable: Throwable?) = Unit

    override fun debug(message: String, throwable: Throwable?) = Unit

    override fun info(message: String, throwable: Throwable?) = Unit

    override fun warn(message: String, throwable: Throwable?) = Unit

    override fun error(message: String, throwable: Throwable?) = Unit

    override fun createLogger(tag: String): Logger = this

    override fun createLogger(clazz: Any): Logger = this
}
