import com.epicbot.api.shared.APIContext
import com.epicbot.api.shared.GameType
import com.epicbot.api.shared.model.Area
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

@ScriptManifest(name = "Blast Furnace Test Script", gameType = GameType.OS)
class BlastFurnace : LoopScript() {
//    var depositItems: ArrayList<*> = ArrayList<Any?>()

  private var task: String = "walkToDispenser"
  private var ore: String = "coal"

  private val BANK_AREA: Area = Area(1946, 4959, 1946, 4959)
  private val CONVEYOR_AREA: Area = Area(1937, 4966, 1937, 4966)
  private val DISPENSER_AREA: Area = Area(1939, 4963, 1939, 4963)

  private var hasBars: Boolean = true
  private var exp: Int = 0

  private var startingExp: Int = 0
  private var expGained: Int = 0
  private var expPerHour: Long = 0
  private var moneyGained: Long = 0
  private var moneyPerHour: Int = 0

  private var startTime: Long = 0
  private var barsMade: Long = 0
  private var staminasUsed: Long = 0

  private var addyBarCost: Int = 0
  private var addyOreCost: Int = 0
  private var coalCost: Int = 0
  private var staminaCost: Int = 0

  override fun onStart(vararg strings: String): Boolean {
    apiContext.walking().setRun(true)
    exp = apiContext.skills().smithing().experience
    startingExp = apiContext.skills().smithing().experience
    startTime = System.currentTimeMillis()

    // addy bar
    addyBarCost = getGeCost(2361)

    // addy ore
    addyOreCost = getGeCost(449)

    // coal
    coalCost = getGeCost(453)

    // stamina dose
    staminaCost = getGeCost(12631)

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


    if (apiContext.skills().smithing().experience > exp) {
      hasBars = true
    }

    exp = apiContext.skills().smithing().experience

    expGained = exp - startingExp
    var timePassed = ((System.currentTimeMillis() - startTime) / 3600000f)
    if (timePassed > 0f) {
      expPerHour = (expGained / timePassed).toLong()
    }

    // get profit from bars
    moneyGained = (((addyBarCost - addyOreCost - (coalCost * 3)) * barsMade) - (staminaCost * staminasUsed) - (timePassed * 72000)).toLong()
    if (timePassed > 0f) {
      moneyPerHour = ((moneyGained / timePassed).toInt())
    }

  }

  private fun doTasks() {

    if (this.task == "walkToBank") {
      walkToBank()
    } else if (this.task == "withdraw") {
      withdraw()
    } else if (this.task == "conveyor") {
      conveyor()
    } else if (this.task == "dispenser") {
      dispenser()
    } else if (this.task == "walkToConveyor") {
      walkToConveyor()
    } else if (this.task == "walkToDispenser") {
      walkToDispenser()
    } else if (this.task == "stamina") {
      stamina()
    }
  }

  private fun walkToBank() {

    apiContext.walking().setRun(true)
    val bankBox = apiContext.objects().query().id(26707).results().nearest()

    if (bankBox.interact("Use")) {
      Time.sleep(20000, Completable { apiContext.bank().isOpen })

      if (apiContext.walking().runEnergy < 70 && !apiContext.localPlayer().isStaminaActive || apiContext.walking().runEnergy < 20) {
        this.task = "stamina"
        return
      }

      print("withdrawing " + this.ore)
      this.task = "withdraw"
    }

    if (apiContext.calculations().distanceTo(bankBox.location) > 8) {
      apiContext.walking().walkOnMap(BANK_AREA.centralTile)
    } else {
      apiContext.bank().open()

      if (apiContext.bank().isOpen) {

        if (apiContext.walking().runEnergy < 70 && !apiContext.localPlayer().isStaminaActive || apiContext.walking().runEnergy < 20) {
          this.task = "stamina"
          return
        }

        print("withdrawing " + this.ore)
        this.task = "withdraw"
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

  private fun stamina() {
    apiContext.bank().open()
    apiContext.bank().depositAll(229)
    apiContext.bank().depositAll(328)
    apiContext.bank().depositAll(449)
    apiContext.bank().depositAll(2361)
    apiContext.bank().depositAll(12631)

    apiContext.bank().withdraw(1, 12631)
    Time.sleep(3000, Completable { apiContext.inventory().contains(12631) })
//    apiContext.bank().close()
    val pot = apiContext.inventory().getItem(12631)
    pot.interact("Drink")
    Time.sleep(3000, Completable { apiContext.inventory().contains(229) })
    this.staminasUsed += 1
    this.task = "withdraw"
  }

  private fun withdraw() {
    apiContext.bank().open()

    // deposit items
    apiContext.bank().depositAll(229)
    apiContext.bank().depositAll(328)
    apiContext.bank().depositAll(449)
    apiContext.bank().depositAll(2361)
    apiContext.bank().depositAll(12631)

    val item = apiContext.inventory().getItem(24480)

    if (item.actions.contains("Fill")) {
      print("filling bag")
      item.interact("Fill")
    }

    if (apiContext.inventory().emptySlotCount != 0) {
      if (ore == "coal") {
        apiContext.bank().withdrawAll(453)
        this.ore = "addy"
      } else {
        apiContext.bank().withdrawAll(449)
        this.ore = "coal"
      }
    }

    Time.sleep(3000, Completable { apiContext.inventory().emptySlotCount == 0 })

    apiContext.bank().close()

    this.task = "walkToConveyor"
  }

  private fun walkToDispenser() {
    val dispenser = apiContext.objects().query().named("Bar dispenser").results().nearest()

    if (hasBars && dispenser.interact("Take")) {
      Time.sleep(8000, Completable { !apiContext.widgets().get(270).isVisible})
      this.task = "dispenser"
      this.hasBars = false
    } else {
      this.hasBars = false
      this.task = "walkToBank"
    }
  }

  private fun dispenser() {

//    val dispenser = apiContext.objects().query().id(9100).results().nearest()
//    dispenser.interact("Take")
    if (apiContext.widgets().get(270).isVisible) {
      apiContext.keyboard().sendKey(49)
      val bankBox = apiContext.objects().query().id(26707).results().nearest()
      bankBox.hover()
      Time.sleep(15000, Completable { apiContext.inventory().emptySlotCount != 27 })
      this.barsMade += 27
      this.task = "walkToBank"
    }
  }

  private fun walkToConveyor() {
    val conveyor = apiContext.objects().query().id(9100).results().nearest()

    if (conveyor.interact("Put-ore-on")) {
      val item = apiContext.inventory().getItem(24480)
      item.hover()
      Time.sleep(20000, Completable { apiContext.inventory().emptySlotCount == 27 })
      this.task = "conveyor"
      return
    }

    if (apiContext.calculations().distanceTo(conveyor.location) > 5) {
      apiContext.walking().walkTo(CONVEYOR_AREA.centralTile)
    } else {
      while (apiContext.inventory().emptySlotCount != 27) {
        conveyor.interact("Put-ore-on")
        Time.sleep(1000, Completable { apiContext.inventory().emptySlotCount == 27 })
      }
      this.task = "conveyor"
    }
  }

  private fun conveyor() {

    val conveyor = apiContext.objects().query().id(9100).results().nearest()
    val item = apiContext.inventory().getItem(24480)
    item.interact("Empty")
    conveyor.hover()
    Time.sleep(2000, Completable { apiContext.inventory().emptySlotCount == 0 })

    while(apiContext.inventory().emptySlotCount != 27) {
      conveyor.interact("Put-ore-on")
      if (hasBars) {
        val dispenser = apiContext.objects().query().named("Bar dispenser").results().nearest()
        dispenser.hover()
      } else {
        val bankBox = apiContext.objects().query().id(26707).results().nearest()
        bankBox.hover()
      }
      Time.sleep(2000, Completable { apiContext.inventory().emptySlotCount == 27 })
    }

    this.task = "walkToDispenser"

    return

  }

  override fun onPaint(g: Graphics2D, ctx: APIContext) {
    val frame = PaintFrame("Blast Furnace")
    frame.addLine("Task", task)
    frame.addLine("Ore", ore)
    frame.addLine("Exp", exp)
    frame.addLine("Exp Gained", expGained)
    frame.addLine("Exp/hr", expPerHour)
    frame.addLine("Money Gained", moneyGained)
    frame.addLine("Money/hr", moneyPerHour)
    frame.draw(g, 0.0, 170.0, ctx)
  }

}