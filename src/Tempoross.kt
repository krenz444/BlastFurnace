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

@ScriptManifest(name = "Tempoross Script", gameType = GameType.OS)
class Tempoross : LoopScript() {
//    var depositItems: ArrayList<*> = ArrayList<Any?>()

  private val WAVE_INCOMING_MESSAGE = "A colossal wave closes in..."
  private val WAVE_END_SAFE = "as the wave washes over you"
  private val WAVE_END_DANGEROUS = "...the wave slams into you"
  private val TEMPOROSS_VULNERABLE_MESSAGE = "Tempoross is vulnerable!"
  private val TEMPOROSS_NOT_VULNERABLE_MESSAGE = "The skies clear as Tempoross retreats to the depths."

  private var task: String = "fish"
  private var phase: String = "start"
  private var initialized: Boolean = false
  private var oldPhase: String = ""
  private var firstDump: Boolean = true
  private var hasAttacked: Boolean = false

  private val BANK_AREA: Area = Area(3137, 2830, 3180, 2850)
  private val BANK_BOAT: Area = Area(3130, 2829, 3135, 2842)

  private lateinit var dumpSpot: Tile
  private lateinit var boatArea: Area
  private lateinit var beachArea: Area
  private lateinit var shrineLocation: Tile
  private lateinit var poolLocation: Tile

  private var exp: Int = 0
  private var expGained: Int = 0
  private var expPerHour: Int = 0
  private var startingExp: Int = 0
  private var startTime: Long = 0

  override fun onStart(vararg strings: String): Boolean {
    apiContext.walking().setRun(true)
    apiContext.camera().yaw = 0
    apiContext.camera().pitch = 98
    // detemine task based on location

    exp = apiContext.skills().fishing().experience
    startingExp = apiContext.skills().fishing().experience
    startTime = System.currentTimeMillis()

    // addy bar
//    addyBarCost = getGeCost(2361)

    // addy ore
//    addyOreCost = getGeCost(449)

    // coal
//    coalCost = getGeCost(453)

    // stamina dose
//    staminaCost = getGeCost(12631)

    return true
  }
  override fun loop(): Int {
//    print("task: $task")
//    print(apiContext.localPlayer().isMoving)
    doCalculations()
    doTasks()

    return 50
  }
  private fun doCalculations() {

//
//    if (apiContext.skills().smithing().experience > exp) {
//      hasBars = true
//    }
//
    exp = apiContext.skills().fishing().experience

    expGained = exp - startingExp
    var timePassed = ((System.currentTimeMillis() - startTime) / 3600000f)
    if (timePassed > 0f) {
      expPerHour = (expGained / timePassed).toInt()
    }
//
//    // get profit from bars
//    moneyGained = (((addyBarCost - addyOreCost - (coalCost * 3)) * barsMade) - (staminaCost * staminasUsed) - (timePassed * 72000)).toLong()
//    if (timePassed > 0f) {
//      moneyPerHour = ((moneyGained / timePassed).toInt())
//    }

  }
  override fun onChatMessage(a: ChatMessageEvent?) {
    if (a != null) {
      if (a.message.text.contains(WAVE_INCOMING_MESSAGE)) {
        oldPhase = phase
        sleepSet("tether")
      } else if (a.message.text.contains("slams into you")) {
        sleepSet("dump")
      } else if (a.message.text.contains("Tempoross is vulnerable")) {
        apiContext.walking().walkTo(poolLocation)
        oldPhase = phase
        sleepSet("damage")
      } else if (a.message.text.contains("Tempoross retreats")) {
        sleepSet("exitGame")
      } else if (a.message.text.contains("You lose some") || a.message.text.contains("You lose a") ) {
        if (!apiContext.inventory().contains("Rope")) {
          sleepSet("dump")
        }
      } else if (a.message.text.contains("You must untether yourself")) {
        oldPhase = phase
        this.phase = "untether"
      } else if (a.message.text.contains("A string wind blows")) {

       }
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

    if (BANK_BOAT.contains(apiContext.localPlayer().location)) {
      task = "Waiting for game"
      if (apiContext.inventory().contains("Bucket")) {
        apiContext.objects().query().nameContains("Water pump").results().nearest().click()
        Time.sleep(4000, Completable { apiContext.localPlayer().isAnimating })
        Time.sleep(200, Completable { !apiContext.localPlayer().isAnimating })
      }
    }

    if (BANK_AREA.contains(apiContext.localPlayer().location)) {
      task = "Going to boat"
      goToBoat()
    }

    if (checkFires()) {
      return
    }

    if (checkData()) {
      return
    }

    when (phase) {
      "start" -> fish()
      "cook" -> cook()
      "fish2" -> fish()
      "tether" -> tether()
      "dump" -> dump()
      "damage" -> damage()
      "exitGame" -> exitGame()
      "fixTether" -> fixTether()
      "untether" -> untether()
    }
  }
  private fun exitGame() {
    //10597
    val exitGuys = apiContext.npcs().query().id(10596).results()
    exitGuys.addAll(apiContext.npcs().query().id(10597).results())
    exitGuys.addAll(apiContext.npcs().query().id(10595).results())
    if (exitGuys.size > 0 && exitGuys.nearest().interact("Leave")) {
      Time.sleep(12000, Completable { BANK_AREA.contains(apiContext.localPlayer().location) })
      phase = "start"
      Time.sleep(2000)
    } else {
      apiContext.walking().walkTo(beachArea.centralTile)
    }
  }
  private fun damage() {
    // 63 56
    // Spirit pool
    if (apiContext.calculations().distanceTo(poolLocation) >= 3) {
      apiContext.walking().walkTo(poolLocation)
      return
    }
    val pool = apiContext.npcs().query().id(10571).results().nearest()

    if (!apiContext.localPlayer().isAnimating && pool != null && apiContext.calculations().distanceTo(pool) < 6) {
      if (pool.click()){
        hasAttacked = true
        Time.sleep(300)
      }
    }
    val emptyPool = apiContext.npcs().query().id(10570).results().nearest()
    if (emptyPool != null && hasAttacked) {
      hasAttacked = false

      if (apiContext.inventory().isFull) {
        phase = "dump"
      } else {
        phase = "fish2"
      }
    }
  }
  private fun dump() {

    if (!apiContext.inventory().contains("Raw harpoonfish") && !apiContext.inventory().contains("Harpoonfish")) {
      phase = "fish2"
    }
    val fishBoxes = apiContext.npcs().query().nameContains("Ammunition crate").results().filter { npc ->
      boatArea.contains(npc.location)
    }

    if (fishBoxes.contains(apiContext.localPlayer().interacting)) {
      return
    }

//      if (apiContext.calculations().distanceTo(fishBoxes.random()) < 8) {
    if (fishBoxes.random().interact()) {
      Time.sleep(400, Completable { fishBoxes.contains(apiContext.localPlayer().interacting) })
//        }
    } else {
      apiContext.walking().walkTo(dumpSpot)
    }


//    if (fishBoxes.random().interact("Fill")) {
//      Time.sleep(2000, Completable { apiContext.localPlayer().isAnimating })
//    } else {
//      apiContext.walking().walkTo(boatArea.centralTile)
//    }
//    if (boatArea.contains(apiContext.localPlayer().location)) {
//      val fishBox = apiContext.npcs().query().nameContains("Ammunition crate").results().random()
//      if (apiContext.calculations().distanceTo(fishBox) < 8) {
//        if (fishBox.click()) {
//          Time.sleep(2000, Completable { apiContext.localPlayer().isAnimating })
//        }
//      }
//    } else {
//      apiContext.walking().walkTo(boatArea.centralTile)
  }

  private fun checkFires() : Boolean {

    // check if a fire is spawning
    // 8876 animation on cloud
    val clouds = apiContext.npcs().query().id(10580).results()
    var escapeCloud = false

    for (cloud in clouds) {
      val cloudLoc = listOf<Locatable>(
        cloud.location,
        Tile(cloud.location.x - 1, cloud.location.y),
        Tile(cloud.location.x, cloud.location.y - 1),
        Tile(cloud.location.x - 1, cloud.location.y - 1)
      )
      if (cloud.animation == 8876) {

        if (cloudLoc.contains(apiContext.localPlayer().location)) {
          print("escaping cloud zap")
          escapeCloud = true
        }
      }
    }

    if (escapeCloud) {
      if (boatArea.contains(apiContext.localPlayer().location)) {
        var randTile = boatArea.randomTile
        while (clouds.any { cloud ->
            listOf<Locatable>(
              cloud.location,
              Tile(cloud.location.x - 1, cloud.location.y),
              Tile(cloud.location.x, cloud.location.y - 1),
              Tile(cloud.location.x - 1, cloud.location.y - 1)
            ).contains(randTile)
        }) {
          randTile = boatArea.randomTile
        }
        apiContext.walking().walkOnScreen(randTile)
      } else {
        var randTile = beachArea.randomTile
        while (clouds.any { cloud ->
            listOf<Locatable>(
              cloud.location,
              Tile(cloud.location.x - 1, cloud.location.y),
              Tile(cloud.location.x, cloud.location.y - 1),
              Tile(cloud.location.x - 1, cloud.location.y - 1)
            ).contains(randTile)
          }) {
          randTile = beachArea.randomTile
        }
        print("walking to : " + randTile.x + " : " + randTile.y)
        apiContext.walking().walkOnScreen(randTile)
      }
      Time.sleep(2000, Completable { apiContext.localPlayer().isMoving })
      Time.sleep(6000, Completable { apiContext.npcs().query().id(8643).results().size > 0 })
      return true
    }

    // stop if fires nearby and were moving
    val fires = apiContext.npcs().query().id(8643).results()

    var douseFire = false
    for (fire in fires) {
      if (apiContext.calculations().distanceTo(fire) < 5) {
        douseFire = true
      } else if (phase == "dump" || apiContext.localPlayer().isMoving || (phase == "fish" && !beachArea.contains(apiContext.localPlayer().location))) {
        if (apiContext.calculations().distanceTo(fire) < 13) {
          douseFire = true
        }
      }
    }
    if (douseFire) {
      if (!apiContext.localPlayer().isMoving && apiContext.inventory().contains("Bucket of water")) {
        val fire = apiContext.npcs().query().id(8643).results().nearest()
        if (fire != null && fire.interact()) {
          Time.sleep(2000, Completable { !fire.isValid })
        }
      }
      return true
    }
    return false
  }
  private fun checkData() : Boolean {
    val data = apiContext.widgets().query().textContains("Energy:").results().first()
    val data2 = apiContext.widgets().query().textContains("Essence:").results().first()

    if (data != null && data2 != null) {
      val energy = data.text.split(" ")[1].replace("%", "")
      val essence = data2.text.split(" ")[1].replace("%", "")
//      print(energy)
      if (phase == "fish2" && essence.toFloat() < 50 && apiContext.inventory().items.count { itemWidget -> itemWidget.name.contains("arpoonfish") } * 2.4 > (energy.toFloat())) {
        phase = "dump"
      }

      if (energy.toInt() < 3) {
        phase = "damage"
      }

    }
    return false
  }
  private fun tether() {
    var tetherObjects = apiContext.objects().query().named("Totem Pole").results()
    tetherObjects.addAll(apiContext.objects().query().named("Mast").results())
    Time.sleep(1600)
    tetherObjects.nearest().hover()
    if (tetherObjects.nearest().interact("Tether")) {
      Time.sleep(5000, Completable { apiContext.localPlayer().isAnimating || phase != "tether"})
      Time.sleep(8000, Completable { apiContext.localPlayer().animation == 832 || phase != "tether"})
      Time.sleep(8000, Completable { (!apiContext.localPlayer().isAnimating) || phase != "tether"})
    }

    this.phase = "fixTether"

  }
  private fun untether() {
    var tetherObjects = apiContext.objects().query().named("Totem Pole").results()
    tetherObjects.addAll(apiContext.objects().query().named("Mast").results())
    if (tetherObjects.nearest().interact("Untether")) {
      Time.sleep(1000, Completable { apiContext.localPlayer().isAnimating || phase != "tether"})
      Time.sleep(2000, Completable { (!apiContext.localPlayer().isAnimating) || phase != "tether"})
    }
    this.phase = oldPhase
  }
  private fun fixTether() {
    // 41011
    var tetherObjects = apiContext.objects().query().id(51011).results()
    tetherObjects.addAll(apiContext.objects().query().nameContains("Damaged mast").results())
    if (tetherObjects.size > 1 && tetherObjects.nearest().interact("Repair")) {
      Time.sleep(3000, Completable { apiContext.localPlayer().isAnimating })
      Time.sleep(3000, Completable { !apiContext.localPlayer().isAnimating })
    }

    if (firstDump) {
      if (oldPhase == "dump") {
        phase = "dump"
      } else if (apiContext.inventory().contains("Raw harpoonfish")) {
        phase = "fish2"
      } else {
        phase = "fish2"
      }
      firstDump = false
    } else {
      if (oldPhase == "dump") {
        phase = "dump"
      }  else {
        phase = "fish2"
      }
    }
  }
  private fun fish() {

    if (!initialized) {
      val mast = apiContext.objects().query().named("Mast").results().nearest()
      if (mast != null) {
        calculateOffsets(mast)
        initialized = true
      }
      return
    }

    if (!beachArea.contains(apiContext.localPlayer().location)) {
      walkToBeach()
    } else {
      val doubleFishingSpot = apiContext.npcs().query().id(10569).results().nearest()
      val fishingSpots = apiContext.npcs().query().id(10565).results()
      fishingSpots.addAll(apiContext.npcs().query().id(10568).results())
      if (doubleFishingSpot != null) {
        fishingSpots.add(doubleFishingSpot)
      }

      if (fishingSpots.size == 0) {
        walkToBeach()
      }

      if (fishingSpots.contains(apiContext.localPlayer().interacting)) {
        // we are fishing
        // change to double spot if it comes
        if (doubleFishingSpot != null && doubleFishingSpot != apiContext.localPlayer().interacting) {
          if (doubleFishingSpot.interact("Harpoon")) {
            Time.sleep(2000
            ) { doubleFishingSpot == apiContext.localPlayer().interacting || phase == "tether" || phase == "damage" }
          }
        }
      } else if (apiContext.inventory().isFull) {
        phase = if (firstDump) {
          "cook"
        } else {
          "dump"
        }
      } else {
        if (doubleFishingSpot != null && doubleFishingSpot.interact("Harpoon")) {
          Time.sleep(2000, Completable { doubleFishingSpot == apiContext.localPlayer().interacting || phase == "tether" || phase == "damage" })
        } else if (fishingSpots.nearest() != null && fishingSpots.nearest().interact("Harpoon")) {
          Time.sleep(2000, Completable { fishingSpots.contains(apiContext.localPlayer().interacting) || phase == "tether" || phase == "damage" })
        }
      }
    }
  }
  private fun cook() {
    val shrine = apiContext.objects().query().id(41236).results().nearest()

    if (!apiContext.inventory().contains("Raw harpoonfish")) {
      phase = "dump"
    }

    if (apiContext.localPlayer().animation != 896) {
      if (shrine.interact("Cook-at")) {
        Time.sleep(1000)
        Time.sleep(10000, Completable { apiContext.localPlayer().isAnimating || phase == "tether" || phase == "damage" })
      } else {
        apiContext.walking().walkTo(shrineLocation)
      }
    }
  }
  private fun calculateOffsets(mastLocation: Locatable) {

//    print("mast x: " + mastLocation.x)
//    print("mast y: " + mastLocation.y)
    if (mastLocation.sceneOffset.x == 52) {

      dumpSpot = Tile(mastLocation.location.x + 2, mastLocation.location.y + 4)

      boatArea = Area(
        (mastLocation.x - mastLocation.sceneOffset.x) + 49,
        (mastLocation.y - mastLocation.sceneOffset.y) + 44,
        (mastLocation.x - mastLocation.sceneOffset.x) + 55,
        (mastLocation.y - mastLocation.sceneOffset.y) + 57
      )

      beachArea = Area(
        (mastLocation.x - mastLocation.sceneOffset.x) + 53,
        (mastLocation.y - mastLocation.sceneOffset.y) + 56,
        (mastLocation.x - mastLocation.sceneOffset.x) + 66,
        (mastLocation.y - mastLocation.sceneOffset.y) + 78
      )

      shrineLocation = Tile(
        (mastLocation.x - mastLocation.sceneOffset.x) + 56,
        (mastLocation.y - mastLocation.sceneOffset.y) + 73
      )

//      fishBoxes = listOf(
//        Tile(
//          (mastLocation.x - mastLocation.sceneOffset.x) + 54,
//          (mastLocation.y - mastLocation.sceneOffset.y) + 50
//        ), Tile(
//          (mastLocation.x - mastLocation.sceneOffset.x) + 54,
//          (mastLocation.y - mastLocation.sceneOffset.y) + 48
//        )
//      )

      poolLocation = Tile(
        (mastLocation.x - mastLocation.sceneOffset.x) + 63,
        (mastLocation.y - mastLocation.sceneOffset.y) + 56
      )
    } else {

      // mast
      // 13842, 2241
      // boat
      // 13841, 2237
      // 13845 ,2246
      // beach
      // 13817, 2217
      // 13832, 2234
      // shrine
      // 13821, 2220
      //pool
      // 13831, 2234
//      boatArea = Area(
//        (mastLocation.x - mastLocation.sceneOffset.x) + 47,
//        (mastLocation.y - mastLocation.sceneOffset.y) + 49,
//        (mastLocation.x - mastLocation.sceneOffset.x) + 53,
//        (mastLocation.y - mastLocation.sceneOffset.y) + 62
//      )

//      print(mastLocation.x - 2)
//      print(mastLocation.y - 4)
//      print(mastLocation.x + 3)
//      print(mastLocation.y + 5)

      dumpSpot = Tile(mastLocation.location.x - 2, mastLocation.location.y - 4)


      boatArea = Area(
        mastLocation.x - 2,
        mastLocation.y - 4,
        mastLocation.x + 3,
        mastLocation.y + 5
      )

      beachArea = Area(
        mastLocation.x - 25,
        mastLocation.y - 24,
        mastLocation.x - 10,
        mastLocation.y - 7
      )



      shrineLocation = Tile(
        mastLocation.x - 21,
        mastLocation.y - 21
      )

      // 72 50
      //72 48
//      fishBoxes = listOf(
//        Tile(
//          (mastLocation.x - mastLocation.sceneOffset.x) + 48,
//          (mastLocation.y - mastLocation.sceneOffset.y) + 48
//        ), Tile(
//          (mastLocation.x - mastLocation.sceneOffset.x) + 48,
//          (mastLocation.y - mastLocation.sceneOffset.y) + 50
//        )
//      )

      poolLocation = Tile(
        mastLocation.x - 11,
        mastLocation.y - 8
      )

    }

  }
  private fun walkToBeach() {
    task = "Walking to Beach"
    if (!apiContext.inventory().contains("Rope") ||
      (!(apiContext.inventory().contains("Harpoon") || apiContext.inventory().contains("Dragon harpoon"))) ||
      !apiContext.inventory().contains("Hammer") ||
      apiContext.inventory().items.count { itemWidget -> itemWidget.name.contains("Bucket of water") } < 3) {
      // get rope
      if (!apiContext.inventory().contains("Rope")) {
        val ropeBox = apiContext.objects().query().id(40965).results().nearest()
        ropeBox.interact("Take")
        Time.sleep(4000, Completable { apiContext.inventory().contains("Rope") })
        return
      }
      // get hammer
      if (!apiContext.inventory().contains("Hammer")) {
        val hammerBox = apiContext.objects().query().id(40964).results().nearest()
        hammerBox.interact("Take")
        Time.sleep(4000, Completable { apiContext.inventory().contains("Hammer") })
        return
      }
      // get harpoon
      if (!apiContext.inventory().contains("Harpoon") && !apiContext.inventory().contains("Dragon harpoon")) {
        val harpoonBox = apiContext.objects().query().id(40967).results().nearest()
        harpoonBox.interact("Take")
        Time.sleep(4000, Completable { apiContext.inventory().contains("Harpoon") })
        return
      }
      //buckets
      val buckets = apiContext.objects().query().id(40966).results().nearest()

      if (apiContext.inventory().items.count { itemWidget -> itemWidget.name.contains("Bucket") } < 3) {
        if (buckets.interact("Take")) {
          Time.sleep(4000, Completable { apiContext.localPlayer().isAnimating })
          Time.sleep(2000, Completable { !apiContext.localPlayer().isAnimating })
        }
        return
      }

      if (apiContext.inventory().contains("Bucket")) {
        apiContext.objects().query().nameContains("Water pump").results().nearest().click()
        Time.sleep(4000, Completable { apiContext.localPlayer().isAnimating })
        Time.sleep(3000, Completable { !apiContext.localPlayer().isAnimating })
      }

    } else {
      apiContext.walking().walkTo(beachArea.centralTile)
    }
  }
  private fun goToBoat() {
    phase = "start"
    initialized = false
    firstDump = true
    hasAttacked = false
    val ladder = apiContext.objects().query().id(41305).results().nearest()
    if (ladder.interact("Quick-climb")) {
      Time.sleep(20000, Completable { BANK_BOAT.contains(apiContext.localPlayer().location) })
      Time.sleep(3000)
    } else {
      apiContext.walking().walkTo(BANK_BOAT.randomTile)
    }
  }
  private fun isIdle(): Boolean {
    if (apiContext.localPlayer().isAnimating || apiContext.localPlayer().isMoving) {
      return false
    }
    return true
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
    val frame = PaintFrame("Tempoross")
//    frame.addLine("oldPhase", oldPhase)
    frame.addLine("Phase", phase)
    frame.addLine("Exp Gained", expGained)
    frame.addLine("Exp per Hour", expPerHour)
    frame.addLine("Fishing Level", apiContext.skills().fishing().currentLevel)
    frame.addLine("Next Level Percent", apiContext.skills().fishing().percentToNextLevel)
    frame.draw(g, 0.0, 170.0, ctx)
  }

}