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

  private var phase: String = ""

  private var gameStarted: Boolean = false
  private var elemental: String = "None"
  private var catalytic: String = "None"
  private var cycle: Int = 1
  private var portalUp: Boolean = false
  private var riftTimer: Int = 120
  private var guardianPower: Int = 0

  private val LOBBY: Area = Area(3611, 9470, 3619, 9480)
  private val MINE_RIGHT: Area = Area(3637, 9500, 3642,9507)
  private val MINE_LEFT: Area = Area(3589, 9500, 3593, 9506)
  private val MAIN_AREA: Area = Area(3598, 9484, 3636, 9519)
  private val WORKBENCH: Tile = Tile(3612, 9487)
  private val CENTER_AREA: Area = Area(3611, 9499, 3619, 9507)
  private val CENTER_TILE: Tile = Tile(3615, 9500)

  private var pouchState: String = "empty"
  private var moneyGained: Int = 0
  private var moneyPerHour: Int = 0
  private var startTime: Long = 0
  private var runecraftingLevel: Int = 0
  private var runecraftingStartingExp: Int = 0
  private var runecraftingExp: Int = 0
  private var runecraftingExpGained: Int = 0
  private var runecraftingExpPerHour: Int = 0
  private var miningStartingExp: Int = 0
  private var miningExp: Int = 0
  private var miningExpGained: Int = 0
  private var miningExpPerHour: Int = 0
  private var craftingStartingExp: Int = 0
  private var craftingExp: Int = 0
  private var craftingExpGained: Int = 0
  private var craftingExpPerHour: Int = 0
  private var runesCrafted: MutableMap<String, Int> = mutableMapOf()
  private lateinit var runePrices: Map<String, Int>

  override fun onStart(vararg strings: String): Boolean {
    apiContext.walking().setRun(true)
    apiContext.camera().yawDeg = 168
    apiContext.camera().pitch = 98

    runecraftingLevel = apiContext.skills().runecrafting().currentLevel
    runecraftingStartingExp = apiContext.skills().runecrafting().experience
    miningStartingExp = apiContext.skills().mining().experience
    craftingStartingExp = apiContext.skills().crafting().experience
    startTime = System.currentTimeMillis()

    if (apiContext.inventory().contains("Guardian fragments")) {
      this.phase = "craftEss"
    } else {
      this.phase = "mine"
    }

    runePrices = mapOf(
      "Air rune" to getGeCost(556),
      "Water rune" to getGeCost(555),
      "Fire rune" to getGeCost(554),
      "Earth rune" to getGeCost(557),
      "Death rune" to getGeCost(560),
      "Blood rune" to getGeCost(565),
      "Nature rune" to getGeCost(561),
      "Law rune" to getGeCost(563),
      "Cosmic rune" to getGeCost(564),
      "Chaos rune" to getGeCost(562),
      "Mind rune" to getGeCost(558),
      "Body rune" to getGeCost(559)
    )

    thread {
      while (true) {
        doCalculations()
        Time.sleep(50)
      }
    }

    return true
  }

  override fun loop(): Int {
    doTasks()
    return 50
  }

  private fun doCalculations() {
    val timePassed = ((System.currentTimeMillis() - startTime) / 3600000f)

    runecraftingExp = apiContext.skills().runecrafting().experience
    runecraftingExpGained = runecraftingExp - runecraftingStartingExp
    if (timePassed > 0f) {
      runecraftingExpPerHour = (runecraftingExpGained / timePassed).toInt()
    }

    craftingExp = apiContext.skills().crafting().experience
    craftingExpGained = craftingExp - craftingStartingExp
    if (timePassed > 0f) {
      craftingExpPerHour = (craftingExpGained / timePassed).toInt()
    }

    miningExp = apiContext.skills().mining().experience
    miningExpGained = miningExp - miningStartingExp
    if (timePassed > 0f) {
      miningExpPerHour = (miningExpGained / timePassed).toInt()
    }

    moneyGained = runesCrafted.toList().sumBy {
      p ->
      runePrices[p.first]?.times(p.second) ?: 0
    }

    if (timePassed > 0f) {
      moneyPerHour = (moneyGained / timePassed).toInt()
    }
    
    runecraftingLevel = apiContext.skills().runecrafting().currentLevel

    val data = apiContext.widgets().query().textContains("Guardian's power:").results().first()
    if (data != null) {
      guardianPower = data.text.split(" ")[2].replace("%", "").toInt()
    }

    val parentWidget = apiContext.widgets().get(746)
    if (parentWidget != null) {
      val elementalWidget = parentWidget.getChild(20)
      val catalyticWidget = parentWidget.getChild(23)
      val portalWidget = parentWidget.getChild(26)
      val timeWidget = parentWidget.getChild(5)

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
      if (timeWidget.text != null && timeWidget.text.contains(":")) {
        riftTimer = (timeWidget.text.split(":")[0].toInt() * 60) + timeWidget.text.split(":")[1].toInt()
      }
    }
  }

  override fun onChatMessage(a: ChatMessageEvent?) {
    if (a != null) {
      if (a.message.text.contains("The rift becomes active!")) {
        gameStarted = true
        pouchState = "empty"
        cycle = 1
      } else if (a.message.text.contains("The Great Guardian was defeated")) {
        gameStarted = false
        riftTimer = 120
        phase = if (apiContext.inventory().contains("Guardian essence") || pouchState != "empty") {
          "craftRunes"
        } else
          "mine"
      } else if (a.message.text.contains("The Great Guardian successfully closed the rift!")) {
        gameStarted = false
        riftTimer = 120
        phase = if (apiContext.inventory().contains("Guardian essence") || pouchState != "empty") {
          "craftRunes"
        } else
          "mine"
      } else if (a.message.text.contains("The Great Guardian successfully closed the rift!")) {
        gameStarted = false
        riftTimer = 120
        phase = if (apiContext.inventory().contains("Guardian essence") || pouchState != "empty") {
          "craftRunes"
        } else
          "mine"
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

    when (phase) {
      "mine" -> mineRight()
      "craftEss" -> craftEss()
      "craftRunes" -> craftRunes()
      "leaveRift" -> leaveRift()
      "mineLeft" -> mineLeft()
    }
  }

  private fun depositRunes() {

    val depositPool = apiContext.objects().query().id(43696).results().nearest()
    if (depositPool == null) {
      apiContext.walking().walkTo(WORKBENCH)
    } else if (depositPool.hover() && depositPool.interact("Deposit-runes")) {
      interactWait(8000, apiContext.inventory().items.count { item -> item.name.contains(" rune") } == 0)
    } else {
      apiContext.walking().walkTo(depositPool.location)
    }

  }

  private fun depositStones() {
    val guardian = apiContext.npcs().query().nameContains("The Great Guardian").results().nearest()
    if (guardian != null && guardian.interact("Power-up")) {
      interactWait(8000, !apiContext.inventory().contains("Elemental guardian stone") &&
              !apiContext.inventory().contains("Catalytic guardian stone"), 1000)
    } else {
      apiContext.walking().walkTo(CENTER_TILE)
    }
  }

  private fun mineLeft() {

    if (!MINE_LEFT.contains(apiContext.localPlayer().location)) {
      if (portalUp) {

        val minePortal = apiContext.objects().query().id(43729).results().nearest()

        if (minePortal == null) {
          if (!CENTER_AREA.contains(apiContext.localPlayer().location)) {
            apiContext.walking().walkTo(CENTER_TILE)
          }
        } else if (minePortal.hover() && minePortal.interact("Enter")) {
          interactWait(7000, MINE_LEFT.contains(apiContext.localPlayer().location), 1000)
        } else {
          apiContext.walking().walkTo(minePortal)
        }
      } else {
        if (guardianPower > 92) {
          if (apiContext.inventory().contains("Elemental guardian stone") ||
            apiContext.inventory().contains("Catalytic guardian stone")
          ) {
            depositStones()
            return
          }
        }
        if (apiContext.inventory().items.count { item -> item.name.contains(" rune") } > 0) {
          depositRunes()
          return
        }
        val guardianRemains = apiContext.objects().query().id(43717).results().nearest()
        if (apiContext.localPlayer().isAnimating) {
          // pass
        } else if (guardianRemains.interact("Mine")) {
          interactWait(4000, apiContext.localPlayer().isAnimating)
        }
      }
    } else if (apiContext.inventory().contains("Guardian essence")) {
      if (apiContext.inventory().getCount("Guardian essence") >= 18) {
        if (pouchState != "full") {
          fillPouch()
          val remains = apiContext.objects().query().id(43720).results().nearest()
          if (remains.interact("Mine")) {
            interactWait(2000, apiContext.localPlayer().isAnimating )
          }
        }
        else {
          if (apiContext.inventory().isFull) {
            val minePortal = apiContext.objects().query().id(38044).results().nearest()
            minePortal.interact("Enter")
            interactWait(3000, CENTER_AREA.contains(apiContext.localPlayer().location), 1000)
            this.phase = "craftRunes"
          }
        }
      }
    } else {
      if (apiContext.localPlayer().isAnimating || apiContext.localPlayer().isMoving) {
        // pass
      } else {
        val remains = apiContext.objects().query().id(43720).results().nearest()
        if (remains.interact("Mine")) {
          interactWait(2000, apiContext.localPlayer().isAnimating)
        }
      }
    }

  }

  private fun fillPouch() {
    val smallPouch = apiContext.inventory().getItem("Small pouch")
    val mediumPouch = apiContext.inventory().getItem("Medium pouch")
    val largePouch = apiContext.inventory().getItem("Large pouch")
    val giantPouch = apiContext.inventory().getItem("Giant pouch")
    if (pouchState == "empty") {
      smallPouch.interact("Fill")
      mediumPouch.interact("Fill")
      largePouch.interact("Fill")
      pouchState = "half"
    } else if (pouchState == "half") {
      giantPouch.interact("Fill")
      pouchState = "full"
    }
    Time.sleep(1000) { !apiContext.inventory().isFull }
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

  private fun craftRunes() {

    if(apiContext.widgets().query().textContains("The guardian is dormant").results().any()) {
      apiContext.keyboard().sendKey(32)
    }

    if (MINE_LEFT.contains(apiContext.localPlayer().location)) {
      // leave left area
      val minePortal = apiContext.objects().query().id(38044).results().nearest()
      minePortal.interact("Enter")
      interactWait(2000, CENTER_AREA.contains(apiContext.localPlayer().location))
      return
    }

    if (elemental == "None") {
      return
    } else if (MAIN_AREA.contains(apiContext.localPlayer().location)) {

      if (apiContext.inventory().contains("Elemental guardian stone") ||
        apiContext.inventory().contains("Catalytic guardian stone")) {
        depositStones()
        return
      }

      val rift = apiContext.objects().query().nameContains(bestRift()).results().nearest()

      if (rift != null && apiContext.calculations().distanceTo(rift.location) - 4 <= riftTimer && rift.hover() && rift.interact("Enter")) {
        interactWait(8000, (!MAIN_AREA.contains(apiContext.localPlayer().location) || riftTimer <= 1), 1000)
      } else {
        if (!CENTER_AREA.contains(apiContext.localPlayer().location)) {
          apiContext.walking().walkTo(CENTER_TILE)
        }
      }
    } else {
      // in a rift, craft runes
      val altar = apiContext.objects().query().nameContains("Altar").results().nearest()

      if (altar == null) {
        apiContext.walking().walkTo(CENTER_TILE)
      } else if ( Time.sleep(500) && altar.hover() && altar.interact("Craft-rune")) {
        Time.sleep(6000) {!apiContext.inventory().contains("Guardian essence")}
        if (apiContext.inventory().isFull) {
          return
        }
        if (pouchState != "empty") {
          val smallPouch = apiContext.inventory().getItem("Small pouch")
          val mediumPouch = apiContext.inventory().getItem("Medium pouch")
          val largePouch = apiContext.inventory().getItem("Large pouch")
          val giantPouch = apiContext.inventory().getItem("Giant pouch")

          smallPouch.interact("Empty")
          mediumPouch.interact("Empty")
          largePouch.interact("Empty")
          Time.sleep(1000) { apiContext.inventory().contains("Guardian essence") }
          altar.interact("Craft-rune")
          Time.sleep(1000) { !apiContext.inventory().contains("Guardian essence") }

          giantPouch.interact("Empty")
          Time.sleep(1000) { apiContext.inventory().contains("Guardian essence") }
          altar.interact("Craft-rune")
          Time.sleep(1000) { !apiContext.inventory().contains("Guardian essence") }
        }

        pouchState = "empty"
        phase = "leaveRift"

        val runeCrafted = apiContext.inventory().items.last { item -> item.name.contains("rune") }
        runesCrafted[runeCrafted.name] = runesCrafted.getOrDefault(runeCrafted.name, 0) + runeCrafted.stackSize
      } else {
        apiContext.walking().walkTo(altar.location)
      }

    }
  }
  
  private fun bestRift() : String {
//    return elemental
    if ((catalytic == "Cosmic" && runecraftingLevel >= 27) || apiContext.inventory().contains("Portal talisman (cosmic)"))
      return "Cosmic"
    if ((catalytic == "Death" && runecraftingLevel >= 65) || apiContext.inventory().contains("Portal talisman (death)"))
      return "Death"
//    if (catalytic == "Blood" && runecraftingLevel >= 77)
//      return catalytic
    if ((catalytic == "Nature" && runecraftingLevel >= 44) || apiContext.inventory().contains("Portal talisman (nature)"))
      return "Nature"
    if ((catalytic == "Law" && runecraftingLevel >= 54) || apiContext.inventory().contains("Portal talisman (law)"))
      return "Law"
    if ((catalytic == "Chaos" && runecraftingLevel >= 35) || apiContext.inventory().contains("Portal talisman (chaos)"))
      return "Chaos"
    if (elemental == "Air")
      return elemental
    if (elemental == "Water")
      return elemental
    if (elemental == "Earth")
      return elemental
    if (elemental == "Fire")
      return elemental
    if (catalytic == "Mind")
      return catalytic
    if (catalytic == "Body")
      return catalytic

    return "None"
  }

  private fun leaveRift() {
    val portal = apiContext.objects().query().named("Portal").results().nearest()

    if (MAIN_AREA.contains(apiContext.localPlayer().location)) {

      phase = if (cycle % 2 == 0) {
        "craftEss"
      } else {
        "mineLeft"
      }
      cycle++

      thread {
        Time.sleep(1000)
        if (apiContext.objects().query().id(43700).results().isNotEmpty()) {
          gameStarted = false
          riftTimer = 120
          phase = "mine"
        }
      }
    } else if (portal.hover() && portal.interact("Use")) {
      interactWait(9000, MAIN_AREA.contains(apiContext.localPlayer().location), 500)
    } else {
      if (portal.id == 43692) {
        // wrong portal
        apiContext.walking().walkTo(CENTER_TILE)
      } else {
        apiContext.walking().walkTo(portal)
      }
    }
  }

  private fun mineRight() {

    if (MINE_LEFT.contains(apiContext.localPlayer().location)) {
      // leave left area
      val minePortal = apiContext.objects().query().id(38044).results().nearest()
      minePortal.interact("Enter")
      interactWait(2000, CENTER_AREA.contains(apiContext.localPlayer().location))
      return
    }

    if (!MAIN_AREA.contains(apiContext.localPlayer().location) && !MINE_RIGHT.contains(apiContext.localPlayer().location)) {
      // in a rift probably, leave
      val portal = apiContext.objects().query().nameContains("Portal").results().nearest()

      if (portal.interact("Use")) {
        interactWait(5000, MAIN_AREA.contains(apiContext.localPlayer().location),500)
      } else {
        apiContext.walking().walkTo(portal)
      }
      return
    }

    // get agility obstacle
    val rocks = apiContext.objects().query().id(43724).results().nearest()

    // if in mining area
//    val fragments = apiContext.inventory().items.find { item -> item.name.contains("Guardian fragments") }?.stackSize

    if (riftTimer <= 31 ) {
      phase = "craftEss"
    } else if (MINE_RIGHT.contains(apiContext.localPlayer().location)) {
      if (apiContext.localPlayer().isAnimating || apiContext.localPlayer().isMoving) {
        // pass
      } else {
        val remains = apiContext.objects().query().id(43719).results().nearest()
        if (gameStarted) {
          if (remains.interact("Mine")) {
            interactWait(3000, apiContext.localPlayer().isAnimating)
          }
        } else {
          remains.interact()
          Time.sleep(30000) { gameStarted }
        }
      }
    } else if (apiContext.inventory().items.count { item -> item.name.contains(" rune") } > 0) {
      depositRunes()
    } else if (rocks != null && rocks.interact()) {
      interactWait(5000, MINE_RIGHT.contains(apiContext.localPlayer().location))
      if (MINE_RIGHT.contains(apiContext.localPlayer().location)) {
        val remains = apiContext.objects().query().id(43719).results().nearest()
        remains.interact()
      }
    } else {

      apiContext.walking().walkTo(MINE_RIGHT.centralTile)
    }
  }

  private fun craftEss() {
    if (MINE_LEFT.contains(apiContext.localPlayer().location)) {
      // leave left area
      val minePortal = apiContext.objects().query().id(38044).results().nearest()
      minePortal.interact("Enter")
      interactWait(2000, CENTER_AREA.contains(apiContext.localPlayer().location))
      return
    }

    if (MINE_RIGHT.contains(apiContext.localPlayer().location)) {
      val rocks = apiContext.objects().query().id(43726).results().nearest()

      if (rocks.interact()) {
        interactWait(8000, apiContext.localPlayer().isAnimating)
      }
    } else {

      if (guardianPower > 92) {
        if (apiContext.inventory().contains("Elemental guardian stone") ||
          apiContext.inventory().contains("Catalytic guardian stone")
        ) {
          depositStones()
          return
        }
      }

      val workbench = apiContext.objects().query().id(43754).results().nearest()
//      val guardianRemains = apiContext.objects().query().id(43717).results().nearest()

      if (apiContext.localPlayer().isAnimating) {
        //pass
      } else if (!apiContext.inventory().contains("Guardian fragments")) {

        if ((apiContext.inventory().contains("Guardian essence") &&
                  apiContext.inventory().getItem("Guardian essence").stackSize < 10) || pouchState != "empty") {
          this.phase = "craftRunes"
        } else {
          this.phase = "mineLeft"
          cycle++
        }
      } else if (apiContext.inventory().isFull) {
        if (pouchState != "full") {
          fillPouch()
        } else {
          this.phase = "craftRunes"
        }
      } else if (apiContext.inventory().items.count { item -> item.name.contains(" rune") } > 0) {
        depositRunes()
      } else if (workbench != null && workbench.interact("Work-at")) {
        interactWait(4000,apiContext.localPlayer().isAnimating)
      } else {
        apiContext.walking().walkTo(WORKBENCH)
      }
    }
  }

  private fun interactWait(time: Int, condition: Boolean, afterWait: Int = 0) {
    Time.sleep(1000) {apiContext.localPlayer().isMoving}
    Time.sleep(time) {!apiContext.localPlayer().isMoving || condition}
    Time.sleep(300)
    Time.sleep(afterWait)
  }

  private fun getGeCost(id: Int): Int {

    val okHttpClient = OkHttpClient()

    val request = Request.Builder()
      .url("https://prices.runescape.wiki/api/v1/osrs/latest?id=$id")
      .build()

    val response = okHttpClient.newCall(request).execute().body?.string()

    val jsonObject: JsonObject = JsonParser().parse(response).asJsonObject
    return jsonObject.get("data").asJsonObject.get(id.toString()).asJsonObject.get("high").asInt
  }

  override fun onPaint(g: Graphics2D, ctx: APIContext) {
    val frame = PaintFrame("Guardians of the Rift")
    frame.addLine("Phase", phase)
    frame.addLine("Time left on rift", riftTimer)
    frame.addLine("Game Started", gameStarted)
    frame.addLine("Pouch state", pouchState)
    frame.addLine("Elemental", elemental)
    frame.addLine("Catalytic", catalytic)
    frame.addLine("Cycle", cycle)
    frame.addLine("Portal up", portalUp)
    frame.addLine("GP gained", moneyGained)
    frame.addLine("GP/HR", moneyPerHour)
    frame.addLine("Runecrafting Level", apiContext.skills().runecrafting().currentLevel)
    frame.addLine("Next Level Percent", apiContext.skills().runecrafting().percentToNextLevel)
    frame.addLine("Crafting Exp Gained", craftingExpGained)
    frame.addLine("Crafting Exp per Hour", craftingExpPerHour)
    frame.addLine("Mining Exp Gained", miningExpGained)
    frame.addLine("Mining Exp per Hour", miningExpPerHour)
    frame.addLine("Runecrafting Exp Gained", runecraftingExpGained)
    frame.addLine("Runecrafting Exp per Hour", runecraftingExpPerHour)
    frame.draw(g, 0.0, 170.0, ctx)
  }

}