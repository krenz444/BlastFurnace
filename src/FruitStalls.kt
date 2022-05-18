import com.epicbot.api.shared.GameType
import com.epicbot.api.shared.model.Tile
import com.epicbot.api.shared.script.LoopScript
import com.epicbot.api.shared.script.ScriptManifest
import com.epicbot.api.shared.util.time.Time

@ScriptManifest(name = "fruit", gameType = GameType.OS)
class FruitStalls : LoopScript() {

  private var runningAway: Boolean = false

  private val right: Int = 1801
  private val left: Int = 1975

  private var stall: String = "left"

  override fun onStart(vararg strings: String): Boolean {
    apiContext.walking().setRun(true)

    return true
  }

  override fun loop(): Int {

//    if (stall == "left") {
//      apiContext.inventory().items.filter { item -> !(item.name.contains("tamina") || item.name.contains("une pouch")) }.forEach { item ->
//        apiContext.inventory().dropItem(item)
//      }
//      left.click()
//      Time.sleep(5000) {apiContext.localPlayer().isAnimating}
//      right.click()
//      stall = "right"
//    } else {
//      apiContext.inventory().items.filter { item -> !(item.name.contains("tamina") || item.name.contains("une pouch")) }.forEach { item ->
//        apiContext.inventory().dropItem(item)
//      }
//      right.click()
//      Time.sleep(5000) {apiContext.localPlayer().isAnimating}
//      left.click()
//      stall = "left"
//    }

    if (apiContext.inventory().items.any { item -> !(item.name.contains("tamina") || item.name.contains("une pouch")) }) {
      apiContext.inventory().dropItem(apiContext.inventory().items.filter { item -> !(item.name.contains("tamina") || item.name.contains("une pouch")) }.random())
      Tile(right, 3607).hover()
      Time.sleep(3000) {apiContext.inventory().isEmpty}
    } else {
      val stall = apiContext.objects().query().id(28823).results().filter { stall -> stall.location.x == right }
      if (stall.isNotEmpty()) {
        stall.random().interact()
        Time.sleep(3000) { !apiContext.inventory().isEmpty }
      }
    }


    return 50
  }
}