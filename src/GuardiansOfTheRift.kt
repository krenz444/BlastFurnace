import com.epicbot.api.shared.APIContext
import com.epicbot.api.shared.GameType
import com.epicbot.api.shared.event.ChatMessageEvent
import com.epicbot.api.shared.model.Area
import com.epicbot.api.shared.model.Tile
import com.epicbot.api.shared.script.LoopScript
import com.epicbot.api.shared.script.ScriptManifest
import com.epicbot.api.shared.util.paint.frame.PaintFrame
import com.epicbot.api.shared.util.time.Time
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.Graphics2D
import kotlin.concurrent.thread
import kotlin.random.Random

@ScriptManifest(name = "Guardians of the Rift Script", gameType = GameType.OS)
class GuardiansOfTheRift : LoopScript() {
//    var depositItems: ArrayList<*> = ArrayList<Any?>()

  private var phase: String = ""
  private var initialized: Boolean = false
//  private var oldPhase: String = ""

  private var gameStarted: Boolean = false
  private var pouchFull: Boolean = false
  private var elemental: String = "None"
  private var catalytic: String = "None"
  private var cycle: Int = 1
  private var portalUp: Boolean = false
  private var inMiddle: Boolean = false

//  private val ROCK_SPOT: Tile = Tile(3041, 4854) // 26208
//  private val ABYSS: Area = Area(3010, 4800, 3080, 4865)
//  private val WILDERNESS: Area = Area(3077, 3523, 3135, 3565)
//  private val WIZARD_SPOT: Tile = Tile(3105, 3559)  //2581
//  private val DITCH: Tile = Tile(3108, 3520)
//  private val EDGEVILLE: Area = Area(3077, 3483, 3122, 3520)
  private val LOBBY: Area = Area(3611, 9470, 3619, 9480)
  private val MINE_RIGHT: Area = Area(3637, 9500, 3642,9507)
  private val MINE_LEFT: Area = Area(3589, 9500, 3593, 9506)
  private val MAIN_AREA: Area = Area(3598, 9484, 3633, 9519)
  private val WORKBENCH: Tile = Tile(3612, 9487)
  private val CENTER_AREA: Area = Area(3611, 9499, 3619, 9507)

  private var pouchFills: Int = 0
  private var exp: Int = 0
  private var expGained: Int = 0
  private var expPerHour: Int = 0
  private var startingExp: Int = 0
  private var startTime: Long = 0
  private var runecraftingLevel: Int = 0

  override fun onStart(vararg strings: String): Boolean {
    apiContext.walking().setRun(true)
    apiContext.camera().yawDeg = 168
    apiContext.camera().pitch = 98

    exp = apiContext.skills().runecrafting().experience
    runecraftingLevel = apiContext.skills().runecrafting().currentLevel
    startingExp = apiContext.skills().runecrafting().experience
    startTime = System.currentTimeMillis()

    if (apiContext.inventory().contains("Guardian fragments")) {
      this.phase = "craftEss"
    } else {
      this.phase = "mine"
    }
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
    
    runecraftingLevel = apiContext.skills().runecrafting().currentLevel

    val parentWidget = apiContext.widgets().get(746)
    if (parentWidget != null) {
      val elementalWidget = parentWidget.getChild(20)
      val catalyticWidget = parentWidget.getChild(23)
      val portalWidget = parentWidget.getChild(26)
      if (elementalWidget != null) {
        when (elementalWidget.materialId) {
          4370 -> elemental = "None"
          4355 -> elemental = "Water"
          4356 -> elemental = "Earth"
          4357 -> elemental = "Fire"
          4353 -> elemental = "Air"
        }
      }
      if (catalyticWidget != null) {
        when (catalyticWidget.materialId) {
          4369 -> catalytic = "None"
          4360 -> catalytic = "Chaos"
          4362 -> catalytic = "Law"
          4364 -> catalytic = "Blood"
          4354 -> catalytic = "Mind"
          4359 -> catalytic = "Cosmic"
          4361 -> catalytic = "Nature"
          4358 -> catalytic = "Body"
          4363 -> catalytic = "Death"
        }
      }
      portalUp = portalWidget.isVisible
    }
  }

  override fun onChatMessage(a: ChatMessageEvent?) {
    if (a != null) {
      if (a.message.text.contains("The rift becomes active!")) {
        gameStarted = true
      } else if (a.message.text.contains("The Great Guardian was defeated")) {
        gameStarted = false
        phase = "mine"
        cycle = 1
      } else if (a.message.text.contains("The Great Guardian successfully closed the rift!")) {
        gameStarted = false
        phase = "mine"
        cycle = 1
      }
    }
  }

  private fun doTasks() {

    if (!apiContext.client().isLoggedIn) {
      return
    }

    if (LOBBY.contains(apiContext.localPlayer().location)) {
      enterGame()
      return
    }

//    if (MAIN_AREA.contains(apiContext.localPlayer().location)) {
//      if (apiContext.inventory().contains(5513) && phase == "mine") {
//        phase = "fixPouches"
//      }
//    }

    when (phase) {
      "mine" -> mineRight()
      "craftEss" -> craftEss()
      "craftRunes" -> craftRunes()
      "leaveRift" -> leaveRift()
      "mineLeft" -> mineLeft()
//      "fixPouches" -> fixPouches()
    }
  }

  private fun craftRunes() {
    if (elemental != "none") {
      enterRift()
    }
  }

  private fun mineLeft() {

    if (!MINE_LEFT.contains(apiContext.localPlayer().location)) {

      if (apiContext.inventory().contains("Elemental guardian stone") ||
        apiContext.inventory().contains("Catalytic guardian stone")) {
        apiContext.walking().walkTo(CENTER_AREA.centralTile)
        val guardian = apiContext.npcs().query().nameContains("The Great Guardian").results().nearest()
        if (guardian != null && guardian.interact("Power-up")) {
          Time.sleep(4000) {!apiContext.inventory().contains("Elemental guardian stone") &&
                  !apiContext.inventory().contains("Catalytic guardian stone")}
        } else {
          apiContext.walking().walkTo(CENTER_AREA.centralTile)
        }
        return
      }

      if (portalUp) {

        val minePortal = apiContext.objects().query().id(43729).results().nearest()

        if (minePortal == null) {
          if (!CENTER_AREA.contains(apiContext.localPlayer().location)) {
            apiContext.walking().walkTo(CENTER_AREA.randomTile)
          }
        } else if (minePortal.hover() && minePortal.interact("Enter")) {
          Time.sleep(1000) { apiContext.localPlayer().isMoving }
          Time.sleep(5500) { MINE_LEFT.contains(apiContext.localPlayer().location) }
          Time.sleep(1000)
        } else {
          apiContext.walking().walkTo(minePortal)
        }
      } else {
        if (apiContext.inventory().items.count { item -> item.name.contains(" rune") } > 0) {
          val depositPool = apiContext.objects().query().id(43696).results().nearest()
          if (depositPool == null) {
            apiContext.walking().walkTo(WORKBENCH)
          } else if (depositPool.hover() && depositPool.interact("Deposit-runes")) {
            Time.sleep(4000) {apiContext.inventory().items.count { item -> item.name.contains(" rune") } == 0}
          } else {
            apiContext.walking().walkTo(depositPool.location)
          }
          return
        }
        val guardianRemains = apiContext.objects().query().id(43717).results().nearest()
        if (apiContext.localPlayer().isAnimating) {
          // pass
        } else if (guardianRemains.interact("Mine")) {
          Time.sleep(1000) {apiContext.localPlayer().isMoving}
          Time.sleep(4000) {apiContext.localPlayer().isAnimating}
        }
      }
    } else if (apiContext.inventory().isFull) {
      if (!pouchFull) {
        val pouches = apiContext.inventory().items.filter { item -> item.name.contains("pouch") }
        for (pouch in pouches) {
          pouch.interact("Fill")
        }
        Time.sleep(1000) { !apiContext.inventory().isFull }
        this.pouchFull = true
        pouchFills++
        val remains = apiContext.objects().query().id(43720).results().nearest()
        if (remains.interact("Mine")) {
          Time.sleep(3000) { apiContext.localPlayer().isAnimating }
        }
      } else {
        val minePortal = apiContext.objects().query().id(38044).results().nearest()
        minePortal.interact("Enter")
        Time.sleep(3000) { CENTER_AREA.contains(apiContext.localPlayer().location) }
        this.phase = "craftRunes"
      }
    } else {
      if (apiContext.localPlayer().isAnimating || apiContext.localPlayer().isMoving) {
        // pass
      } else {
        val remains = apiContext.objects().query().id(43720).results().nearest()
        if (remains.interact("Mine")) {
          Time.sleep(3000) { apiContext.localPlayer().isAnimating }
        }
      }
    }

  }

  private fun enterGame() {
    val entrance = apiContext.objects().query().id(43700).results().nearest()

    if (entrance != null && entrance.interact()) {
      Time.sleep(Random.nextInt(200, 400)) { MAIN_AREA.contains(apiContext.localPlayer().location)}
    } else {
      apiContext.objects().all.random().hover()
      Time.sleep(40000) {apiContext.objects().query().id(43700).results().size > 1}
    }
  }

  private fun enterRift() {
    if (MAIN_AREA.contains(apiContext.localPlayer().location)) {

      val rift = apiContext.objects().query().nameContains(bestRift()).results().nearest()

      if (rift != null && rift.interact("Enter")) {
        Time.sleep(1000) { apiContext.localPlayer().isMoving }
        Time.sleep(8000) { !MAIN_AREA.contains(apiContext.localPlayer().location)  || !apiContext.localPlayer().isMoving}
        Time.sleep(1000)
      } else {
        if (!CENTER_AREA.contains(apiContext.localPlayer().location)) {
          apiContext.walking().walkTo(MAIN_AREA.centralTile)
        }
      }
    } else {
      // in a rift, craft runes
      val altar = apiContext.objects().query().nameContains("Altar").results().nearest()
      if (altar == null) {
        return
      } else if (altar.hover() && altar.interact("Craft-rune")) {
        Time.sleep(6000) {!apiContext.inventory().contains("Guardian essence")}
        if (apiContext.inventory().isFull) {
          return
        }
        val pouches = apiContext.inventory().items.filter { item -> item.name.contains("pouch") }
        for (pouch in pouches) {
          pouch.interact("Empty")
        }
        Time.sleep(1000) {apiContext.inventory().contains("Guardian essence")}
        altar.interact("Craft-rune")
        Time.sleep(1000) {!apiContext.inventory().contains("Guardian essence")}
        pouchFull = false
        phase = "leaveRift"
      } else {
        apiContext.walking().walkTo(altar.location)
      }

    }
  }
  
  private fun bestRift() : String {
    // real best

    if (catalytic == "Death" && runecraftingLevel >= 65)
      return catalytic
//    if (catalytic == "Blood" && runecraftingLevel >= 77)
//      return catalytic
    if (catalytic == "Nature" && runecraftingLevel >= 44)
      return catalytic
    if (elemental == "Fire")
      return elemental

    if (catalytic == "Law" && runecraftingLevel >= 54)
      return catalytic
    if (elemental == "Earth")
      return elemental
    if (catalytic == "Cosmic" && runecraftingLevel >= 27)
      return catalytic

    if (elemental == "Water")
      return elemental
    if (catalytic == "Chaos" && runecraftingLevel >= 35)
      return catalytic
    if (catalytic == "Mind")
      return catalytic
    if (elemental == "Air")
      return elemental
    if (catalytic == "Body")
      return catalytic

//    if (catalytic == "Death" && runecraftingLevel >= 65)
//      return catalytic
//    if (catalytic == "Law" && runecraftingLevel >= 54)
//      return catalytic
//    if (catalytic == "Nature" && runecraftingLevel >= 44)
//      return catalytic
//    if (elemental == "Fire")
//      return elemental
//    if (catalytic == "Cosmic" && runecraftingLevel >= 27)
//      return catalytic
//    if (catalytic == "Chaos" && runecraftingLevel >= 35)
//      return catalytic
//    if (elemental == "Earth")
//      return elemental
//    if (catalytic == "Mind")
//      return catalytic
//    if (catalytic == "Body")
//      return catalytic
//
//
//    if (catalytic == "Blood" && runecraftingLevel >= 77)
//      return catalytic
//
//    if (elemental == "Water")
//      return elemental
//    if (elemental == "Air")
//      return elemental

    return "None"
  }

  private fun leaveRift() {
    val portal = apiContext.objects().query().nameContains("Portal").results().nearest()

    if (MAIN_AREA.contains(apiContext.localPlayer().location)) {

      phase = if (cycle % 2 == 0) {
        "craftEss"
      } else {
        "mineLeft"
      }
      cycle++

      if (!apiContext.widgets().get(746).getChild(0).isVisible) {
        gameStarted = false
        phase = "mine"
        cycle = 1
      }
    } else if (portal.hover() && portal.interact("Use")) {
      Time.sleep(1000)
      Time.sleep(9000) {MAIN_AREA.contains(apiContext.localPlayer().location) || !apiContext.localPlayer().isMoving}
      Time.sleep(2000)
    } else {
      apiContext.walking().walkTo(portal)
    }
  }

  private fun mineRight() {

    if (MINE_LEFT.contains(apiContext.localPlayer().location)) {
      // leave left area
      val minePortal = apiContext.objects().query().id(38044).results().nearest()
      minePortal.interact("Enter")
      Time.sleep(3000) { CENTER_AREA.contains(apiContext.localPlayer().location) }
      return
    }

    if (!MAIN_AREA.contains(apiContext.localPlayer().location) && !MINE_RIGHT.contains(apiContext.localPlayer().location)) {
      // in a rift probably, leave
      val portal = apiContext.objects().query().nameContains("Portal").results().nearest()

      if (portal.interact("Use")) {
        Time.sleep(5000) {MAIN_AREA.contains(apiContext.localPlayer().location)}
        Time.sleep(500)
      } else {
        apiContext.walking().walkTo(portal)
      }
      return
    }

    // get agility obstacle
    val rocks = apiContext.objects().query().id(43724).results().nearest()

    // if in mining area
    val fragments = apiContext.inventory().items.find { item -> item.name.contains("Guardian fragments") }?.stackSize

    if (MINE_LEFT.contains(apiContext.localPlayer().location)) {
      val minePortal = apiContext.objects().query().id(38044).results().nearest()
      minePortal.interact("Enter")
      Time.sleep(3000) { CENTER_AREA.contains(apiContext.localPlayer().location) }
    }

    if (fragments != null && fragments > 135 ) {
      phase = "craftEss"
    } else if (MINE_RIGHT.contains(apiContext.localPlayer().location)) {
      if (apiContext.localPlayer().isAnimating || apiContext.localPlayer().isMoving) {
        // pass
      } else {
        val remains = apiContext.objects().query().id(43719).results().nearest()
        if (gameStarted) {
          if (remains.interact("Mine")) {
            Time.sleep(3000) { apiContext.localPlayer().isAnimating }
          }
        } else {
          remains.interact()
          Time.sleep(30000) { gameStarted }
        }
      }
    } else if (rocks != null && rocks.interact()) {
      Time.sleep(1000) {apiContext.localPlayer().isMoving}
      Time.sleep(6000) {!apiContext.localPlayer().isMoving || MINE_RIGHT.contains(apiContext.localPlayer().location)}
      if (MINE_RIGHT.contains(apiContext.localPlayer().location)) {
        val remains = apiContext.objects().query().id(43719).results().nearest()
        remains.interact()
      }
    } else {

      apiContext.walking().walkTo(MINE_RIGHT.centralTile)
    }
  }

  private fun craftEss() {

    if (MINE_RIGHT.contains(apiContext.localPlayer().location)) {
      val rocks = apiContext.objects().query().id(43726).results().nearest()

      if (rocks.interact()) {
        Time.sleep(1000) {apiContext.localPlayer().isMoving}
        Time.sleep(8000) {apiContext.localPlayer().isAnimating || !apiContext.localPlayer().isMoving}
        Time.sleep(4000) {!apiContext.localPlayer().isAnimating}
      }
    } else {
      val workbench = apiContext.objects().query().id(43754).results().nearest()
      val guardianRemains = apiContext.objects().query().id(43717).results().nearest()

      if (apiContext.calculations().distanceTo(guardianRemains) > 2 && apiContext.localPlayer().isAnimating) {
        //pass
      } else if (apiContext.localPlayer().isAnimating && apiContext.inventory().contains("Guardian fragments") && apiContext.inventory().getItem("Guardian fragments").stackSize < 10) {
        //pass
      } else if (!apiContext.inventory().contains("Guardian fragments")) {

        if (!apiContext.localPlayer().isAnimating && apiContext.calculations().distanceTo(guardianRemains) > 2) {
          guardianRemains.interact()
          Time.sleep(4000) {apiContext.localPlayer().isAnimating }
        }
      } else if (apiContext.inventory().isFull) {
        if (!pouchFull) {
          val pouches = apiContext.inventory().items.filter { item -> item.name.contains("pouch") }
          for (pouch in pouches) {
            pouch.interact("Fill")
          }
          pouchFills++
          Time.sleep(1000) { !apiContext.inventory().isFull }
          this.pouchFull = true
        } else {
          this.phase = "craftRunes"
        }
      } else if (apiContext.inventory().contains("Elemental guardian stone") ||
        apiContext.inventory().contains("Catalytic guardian stone")) {
        apiContext.walking().walkTo(CENTER_AREA.centralTile)
        val guardian = apiContext.npcs().query().nameContains("The Great Guardian").results().nearest()
        if (guardian != null && guardian.interact("Power-up")) {
          Time.sleep(8000) {!apiContext.inventory().contains("Elemental guardian stone") &&
                  !apiContext.inventory().contains("Catalytic guardian stone")}
        } else {
          apiContext.walking().walkTo(CENTER_AREA.centralTile)
        }
        val talismans = apiContext.inventory().items.filter { item -> item.name.contains("Portal talisman")}
        if (talismans.isNotEmpty()) {
          for (talisman in talismans ) {
            apiContext.inventory().dropItem(talisman)
          }
        }
      } else if (apiContext.inventory().items.count { item -> item.name.contains(" rune") } > 0) {
        val depositPool = apiContext.objects().query().id(43696).results().nearest()
        if (depositPool == null) {
          apiContext.walking().walkTo(WORKBENCH)
        } else if (depositPool.hover() && depositPool.interact("Deposit-runes")) {
          Time.sleep(4000) {apiContext.inventory().items.count { item -> item.name.contains(" rune") } == 0}
        } else {
          apiContext.walking().walkTo(depositPool.location)
        }
      } else if (workbench != null && workbench.interact("Work-at")) {
        Time.sleep(1000) {apiContext.localPlayer().isMoving}
        Time.sleep(4000) {apiContext.localPlayer().isAnimating || !apiContext.localPlayer().isMoving}
      } else {
        apiContext.walking().walkTo(WORKBENCH)
      }
    }
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
    frame.addLine("Phase", phase)
    frame.addLine("Game Started", gameStarted)
    frame.addLine("Pouch is Full", pouchFull)
    frame.addLine("Elemental", elemental)
    frame.addLine("Catalytic", catalytic)
    frame.addLine("Cycle", cycle)
    frame.addLine("Portal up", portalUp)
    frame.addLine("Exp Gained", expGained)
    frame.addLine("Exp per Hour", expPerHour)
    frame.addLine("Pouch Fills", pouchFills)
    frame.addLine("Runecrafting Level", apiContext.skills().runecrafting().currentLevel)
    frame.addLine("Next Level Percent", apiContext.skills().runecrafting().percentToNextLevel)
    frame.draw(g, 0.0, 170.0, ctx)
  }

}