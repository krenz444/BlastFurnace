import com.epicbot.api.shared.APIContext
import com.epicbot.api.shared.GameType
import com.epicbot.api.shared.model.Area
import com.epicbot.api.shared.model.Tile
import com.epicbot.api.shared.script.LoopScript
import com.epicbot.api.shared.script.ScriptManifest
import com.epicbot.api.shared.util.details.Completable
import com.epicbot.api.shared.util.paint.frame.PaintFrame
import com.epicbot.api.shared.util.time.Time
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Graphics2D
import kotlin.concurrent.thread

@ScriptManifest(name = "Sand Crab", gameType = GameType.OS)
class SandCrabs : LoopScript() {

  private var runningAway: Boolean = false

  override fun onStart(vararg strings: String): Boolean {
    apiContext.walking().setRun(true)

    thread {
      loop2()
    }

    return true
  }

  private fun loop2() {
    while (true) {
      // do stuff
      Time.sleep(600000)
      runningAway = true
      print("run away and come back")
    }
  }

  override fun loop(): Int {

    // food
    if (runningAway) {
      if (apiContext.calculations().distanceTo(Tile(1730, 3501)) < 3) {
        runningAway = false
        if (!apiContext.inventory().items.any {
            itemWidget -> itemWidget.name.contains("wine")
        }) {
          apiContext.game().logout()
          apiContext.script().stop("beep")
        }
      }
      apiContext.walking().walkTo(Tile(1730, 3501))
    } else if (apiContext.localPlayer().healthPercent < 50) {
      val wines = apiContext.inventory().items.filter {
        itemWidget -> itemWidget.name.contains("wine")
      }
      if (wines.isNotEmpty()) {
        wines.random().click()
        Time.sleep(2000)
      } else {
        runningAway = true
      }
    } else if (apiContext.npcs().query().nameContains("Sand Crab").results().any { crab -> crab.isInteractingWithMe }) {
      //battle
    } else {
      val crab = apiContext.npcs().query().nameContains("Sandy rocks").results().nearest()
      if (crab != null) {
        crab.click()
        Time.sleep(20000) {apiContext.npcs().query().id(7206).results().any { crab -> crab.isInteractingWithMe }}
      } else {
        apiContext.walking().walkTo(Tile(1690,3479))
      }
    }


    //1730, 3501

    return 50
  }
}