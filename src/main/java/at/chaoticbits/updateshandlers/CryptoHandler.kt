package at.chaoticbits.updateshandlers

import at.chaoticbits.coinmarket.CoinMarketScheduler
import at.chaoticbits.config.Bot
import at.chaoticbits.coinmarket.CoinMarketCapService
import com.google.common.base.Strings
import com.vdurmont.emoji.EmojiParser
import org.telegram.telegrambots.api.methods.send.SendMessage
import org.telegram.telegrambots.api.methods.send.SendPhoto
import org.telegram.telegrambots.api.objects.Message
import org.telegram.telegrambots.api.objects.Update
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.exceptions.TelegramApiException
import org.telegram.telegrambots.logging.BotLogger

import java.io.InputStream
import java.util.Timer


/**
 * Crypto Polling Bot, that processes currency requests
 */
class CryptoHandler : TelegramLongPollingBot() {
    /**
     * Instantiate CryptoHandler and start coin market scheduler
     */
    init {

        val cmScheduler = CoinMarketScheduler()

        val initialDelay = 100
        val fixedRate = 60 * 60 * 1000 // every hour
        Timer().schedule(cmScheduler, initialDelay.toLong(), fixedRate.toLong())
    }


    override fun onUpdateReceived(update: Update) {

        //check if the update has a message
        if (update.hasMessage()) {
            val message = update.message

            //check if the message has text. it could also  contain for example a location ( message.hasLocation() )
            if (message.hasText()) {

                //create a object that contains the information to send back the message
                val sendMessageRequest = SendMessage()
                sendMessageRequest.enableMarkdown(true)
                sendMessageRequest.setChatId(message.chatId!!)

                val command = message.text

                try {

                    // request currency details as a formatted string
                    if (!Strings.isNullOrEmpty(Bot.config.stringCommand) && command.startsWith(Bot.config.stringCommand!!)) {

                        sendMessageRequest.text = EmojiParser.parseToUnicode(
                                CoinMarketCapService.getFormattedCurrencyDetails(
                                        command.substring(Bot.config.stringCommand!!.length, getCurrencyEnd(command))))
                        sendMessage(sendMessageRequest)

                        // request currency details as a rendered image
                    } else if (!Strings.isNullOrEmpty(Bot.config.imageCommand) && command.startsWith(Bot.config.imageCommand!!)) {

                        val imageInputStream = CoinMarketCapService.getCurrencyDetailsImage(
                                command.substring(Bot.config.imageCommand!!.length, getCurrencyEnd(command)))

                        val photo = SendPhoto()
                        photo.setChatId(message.chatId!!)
                        photo.setNewPhoto(command, imageInputStream)
                        sendPhoto(photo)
                    }

                } catch (e: Exception) {
                    val errorMessage = e.message
                    BotLogger.error(LOGTAG, errorMessage)

                    // replace '_' characters because of telegram markdown
                    sendMessageRequest.text = errorMessage?.replace("_".toRegex(), "\\\\_")

                    try {
                        sendMessage(sendMessageRequest)
                    } catch (te: TelegramApiException) {
                        BotLogger.error(LOGTAG, te.message)
                    }

                }

            }
        }
    }


    /**
     * Determine the end index of the provided currency slug
     * @param command currency
     * @return index of currency end
     */
    private fun getCurrencyEnd(command: String): Int {
        return if (command.indexOf('@') == -1) command.length else command.indexOf('@')
    }

    override fun getBotUsername(): String? {
        return Bot.config.botName
    }

    override fun getBotToken(): String {
        return System.getenv("CMBOT_TELEGRAM_TOKEN")
    }

    companion object {

        private val LOGTAG = "CryptoHandler"
    }
}