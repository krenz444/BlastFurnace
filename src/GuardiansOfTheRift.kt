import com.epicbot.api.shared.APIContext
import com.epicbot.api.shared.GameType
import com.epicbot.api.shared.entity.details.Locatable
import com.epicbot.api.shared.event.ChatMessageEvent
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

@ScriptManifest(name = "Guardians of the Rift Script", gameType = GameType.OS)
class GuardiansOfTheRift : LoopScript() {
//    var depositItems: ArrayList<*> = ArrayList<Any?>()

  private var phase: String = "start"
  private var initialized: Boolean = false
//  private var oldPhase: String = ""

  private var gameStarted: Boolean = false
  private var pouchFull: Boolean = false

  private val MINE_RIGHT: Area = Area(3637, 9500, 3642,9507)
  private val MINE_LEFT: Area = Area(3137, 2830, 3180, 2850)
  private val MAIN_AREA: Area = Area(3598, 9484, 3633, 9519)
  private val WORKBENCH: Tile = Tile(3612, 9487)

  private var exp: Int = 0
  private var expGained: Int = 0
  private var expPerHour: Int = 0
  private var startingExp: Int = 0
  private var startTime: Long = 0

  override fun onStart(vararg strings: String): Boolean {
    apiContext.walking().setRun(true)
    apiContext.camera().yaw = 0
    apiContext.camera().pitch = 98

    exp = apiContext.skills().runecrafting().experience
    startingExp = apiContext.skills().runecrafting().experience
    startTime = System.currentTimeMillis()

    this.phase = "mine"

    return true
  }

  override fun loop(): Int {
    doCalculations()
    doTasks()

    return 50
  }

  private fun doCalculations() {

    exp = apiContext.skills().runecrafting().experience

    expGained = exp - startingExp
    val timePassed = ((System.currentTimeMillis() - startTime) / 3600000f)
    if (timePassed > 0f) {
      expPerHour = (expGained / timePassed).toInt()
    }
  }

  override fun onChatMessage(a: ChatMessageEvent?) {
    if (a != null) {
      if (a.message.text.contains("The rift becomes active!")) {
        gameStarted = true
      } else if (a.message.text.contains("The Great Guardian was defeated")) {
        gameStarted = false
        this.phase = "mine"
      }
//        sleepSet("dump")
//      } else if (a.message.text.contains("Tempoross is vulnerable")) {
//        apiContext.walking().walkTo(poolLocation)
//        oldPhase = phase
//        sleepSet("damage")
//      } else if (a.message.text.contains("Tempoross retreats")) {
//        sleepSet("exitGame")
//      } else if (a.message.text.contains("You lose some") || a.message.text.contains("You lose a")) {
//        if (!apiContext.inventory().contains("Rope")) {
//          sleepSet("dump")
//        }
//      } else if (a.message.text.contains("You must untether yourself")) {
//        oldPhase = phase
//        this.phase = "untether"
//      } else if (a.message.text.contains("A string wind blows")) {
//        // todo: look to maybe do some fire mitigation if moving
//      }
    }
  }

  private fun sleepSet(phase: String) {
    this.phase = phase
    for (i in 0..60) {
      Time.sleep(100)
      this.phase = phase
    }
  }

  private fun doTasks() {

    if (!apiContext.client().isLoggedIn) {
      return
    }
//
//    if (BANK_BOAT.contains(apiContext.localPlayer().location)) {
//      task = "Waiting for game"
//      phase = "start"
//      if (apiContext.inventory().contains("Bucket")) {
//        apiContext.objects().query().nameContains("Water pump").results().nearest().click()
//        Time.sleep(4000) { apiContext.localPlayer().isAnimating }
//        Time.sleep(200) { !apiContext.localPlayer().isAnimating }
//      }
//    }
//
//    if (BANK_AREA.contains(apiContext.localPlayer().location)) {
//      task = "Going to boat"
//      goToBoat()
//    }

    if (checkData()) {
      return
    }

    when (phase) {
      "mine" -> mine()
      "craftEss" -> craftEss()
    }
  }

  private fun mine() {

    // get agility obstacle
    val rocks = apiContext.objects().query().id(43724).results().nearest()

    // if in mining area
    val fragments = apiContext.inventory().items.find { item -> item.name.contains("Guardian fragments") }?.stackSize

    if (fragments != null && fragments > 20 ) {
      phase = "craftEss"
    } else if (MINE_RIGHT.contains(apiContext.localPlayer().location)) {
      if (apiContext.localPlayer().isAnimating || apiContext.localPlayer().isMoving) {
        // pass
      } else {
        if (gameStarted) {
          val remains = apiContext.objects().query().id(43719).results().nearest()
          if (remains.interact("Mine")) {
            Time.sleep(3000) { apiContext.localPlayer().isAnimating }
          }
        }
      }
    } else if (rocks != null && rocks.interact()) {
      Time.sleep(1000) {apiContext.localPlayer().isMoving}
      Time.sleep(2000) {apiContext.localPlayer().isAnimating}
      Time.sleep(4000) {!apiContext.localPlayer().isAnimating}
      //wait for rocks
    } else {

      apiContext.walking().walkTo(MINE_RIGHT.centralTile)
    }
  }

  private fun craftEss() {
    if (MINE_RIGHT.contains(apiContext.localPlayer().location)) {
      val rocks = apiContext.objects().query().id(43726).results().nearest()

      if (rocks.interact()) {
        Time.sleep(1000) {apiContext.localPlayer().isMoving}
        Time.sleep(8000) {apiContext.localPlayer().isAnimating}
        Time.sleep(4000) {!apiContext.localPlayer().isAnimating}
      }
    } else {
      val workbench = apiContext.objects().query().id(43754).results().nearest()

      if (apiContext.localPlayer().isAnimating) {
        //pass
        // todo check if we have fragmentst
      } else if (apiContext.inventory().isFull) {
        if (!pouchFull) {
          val pouches = apiContext.inventory().items.filter { item -> item.name.contains("pouch") }
          for (pouch in pouches) {
            pouch.interact("Fill")
          }
          this.pouchFull = true
        } else {
          this.phase = "craftRunes"
        }
      } else if (workbench!= null && workbench.interact()) {
        Time.sleep(1000) {apiContext.localPlayer().isMoving}
        Time.sleep(8000) {apiContext.localPlayer().isAnimating}
      } else {
        apiContext.walking().walkTo(WORKBENCH)
      }
    }
  }

  private fun checkData(): Boolean {
    val data = apiContext.widgets().query().textContains("Energy:").results().first()
    val data2 = apiContext.widgets().query().textContains("Essence:").results().first()

    if (data != null && data2 != null) {
      val energy = data.text.split(" ")[1].replace("%", "")
      val essence = data2.text.split(" ")[1].replace("%", "")
//      print(energy)
      if (phase == "fish2" && essence.toFloat() < 50 && apiContext.inventory().items.count { itemWidget ->
          itemWidget.name.contains(
            "arpoonfish"
          )
        } * 2.4 > (energy.toFloat())) {
        phase = "dump"
      }

      if (energy.toInt() < 3) {
        phase = "damage"
      }

    }
    return false
  }

  private fun getGeCost(id: Int): Int {

    print("getting cost of $id")

    val okHttpClient = OkHttpClient()

    val request = Request.Builder()
      .url("https://prices.runescape.wiki/api/v1/osrs/latest?id=$id")
      .build()

    val response = okHttpClient.newCall(request).execute().body?.string()

    val jsonObject: JsonObject = JsonParser().parse(response).getAsJsonObject()
    return jsonObject.get("data").asJsonObject.get(id.toString()).asJsonObject.get("high").asInt
  }

  override fun onPaint(g: Graphics2D, ctx: APIContext) {
    val frame = PaintFrame("Guardians of the Rift")
//    frame.addLine("oldPhase", oldPhase)
    frame.addLine("Phase", phase)
    frame.addLine("Exp Gained", expGained)
    frame.addLine("Exp per Hour", expPerHour)
    frame.addLine("Fishing Level", apiContext.skills().fishing().currentLevel)
    frame.addLine("Next Level Percent", apiContext.skills().fishing().percentToNextLevel)
    frame.draw(g, 0.0, 170.0, ctx)
  }

}