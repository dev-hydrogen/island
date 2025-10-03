package cc.pe3epwithyou.trident.state

import cc.pe3epwithyou.trident.client.TridentClient
import cc.pe3epwithyou.trident.state.fishing.Augment
import cc.pe3epwithyou.trident.state.fishing.OverclockTexture
import cc.pe3epwithyou.trident.state.fishing.UseCondition
import cc.pe3epwithyou.trident.state.fishing.PerkState
import cc.pe3epwithyou.trident.state.fishing.PerkStateCalculator
import cc.pe3epwithyou.trident.state.fishing.PlayerUpgrades
import cc.pe3epwithyou.trident.state.fishing.SpotState
import cc.pe3epwithyou.trident.state.fishing.UpgradeLine
import cc.pe3epwithyou.trident.net.McciApi

import cc.pe3epwithyou.trident.utils.ChatUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import net.fabricmc.loader.api.FabricLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

@Serializable
data class Bait(var type: Rarity = Rarity.COMMON, var amount: Int? = null)

@Serializable
data class Line(var type: Rarity = Rarity.COMMON, var uses: Int? = null)

@Serializable
data class UnstableOverclock(
    var texture: OverclockTexture? = null,
    var state: OverclockState = OverclockState(
        isAvailable = false,
        duration = 60 * 5 * 20,
        timeLeft = 0,
        cooldownLeft = 0,
        cooldownDuration = 60 * 45 * 20,
        isActive = false,
        isCooldown = false,
        level = null
    )
)

@Serializable
data class SupremeOverclock(
    var state: OverclockState = OverclockState(
        isAvailable = false,
        duration = 60 * 10 * 20,
        timeLeft = 0,
        cooldownLeft = 0,
        cooldownDuration = 60 * 60 * 20,
        isCooldown = false,
        isActive = false,
        level = null
    )
)

@Serializable
data class OverclockState(
    var isAvailable: Boolean,
    var duration: Long,
    var timeLeft: Long,
    var cooldownLeft: Long,
    var cooldownDuration: Long,
    var isActive: Boolean,
    var isCooldown: Boolean,
    var level: Int? = null,
)

@Serializable
data class Overclocks(
    var hook: Augment? = null,
    var magnet: Augment? = null,
    var rod: Augment? = null,
    var unstable: UnstableOverclock = UnstableOverclock(),
    var supreme: SupremeOverclock = SupremeOverclock(),
    var stableLevels: StableOverclockLevels = StableOverclockLevels()
)

@Serializable
data class StableOverclockLevels(
    var hook: Int? = null,
    var magnet: Int? = null,
    var rod: Int? = null,
)

@Serializable
data class Supplies(
    var bait: Bait = Bait(),
    var line: Line = Line(),
    var augments: MutableList<MutableAugment> = mutableListOf(),
    var augmentsAvailable: Int = 0,
    var overclocks: Overclocks = Overclocks(),
    var baitDesynced: Boolean = true,
    var needsUpdating: Boolean = true,
)

@Serializable
data class PlayerState(
    var supplies: Supplies = Supplies(),
    var upgrades: PlayerUpgrades = PlayerUpgrades(),
    var fishCollection: PlayerFishCollection = PlayerFishCollection(),
    var exchange: ExchangeState = ExchangeState(),
    @Transient var perkState: PerkState = PerkState(),
    @Transient var spot: SpotState = SpotState(),
    @Transient var inGrotto: Boolean = false,
    @Transient var ingameMinutes: Int = 0,
    @Transient var isDayTime: Boolean = true,
    @Transient var currentCollection: String? = null,
    @Transient var tideLines: MutableSet<UpgradeLine> = mutableSetOf(),
    @Transient var windLines: MutableSet<UpgradeLine> = mutableSetOf(),
    @Transient var magnetPylonBonus: Int = 0,
    @Transient var magnetPylonTimeLeftSeconds: Int = 0,
    @Transient var dailyMeter: MeterState = MeterState(),
    @Transient var weeklyMeter: MeterState = MeterState(),
)

@Serializable
data class MutableAugment(
    val augment: Augment,
    var usesCurrent: Int? = null,
    var usesMax: Int? = null,
    var useCondition: UseCondition? = null,
    var paused: Boolean = false,
    var bannedInGrotto: Boolean = false,
    var repairedBefore: Boolean = false,
    var broken: Boolean = false,
    var repairCost: Int? = null,
)

data class MeterState(
    var progressCurrent: Int = 0,
    var progressTarget: Int = 0,
    var claimsCurrent: Int = 0,
    var claimsMax: Int = 0,
    var sessionXpGained: Long = 0,
    var sessionStartMs: Long = System.currentTimeMillis(),
    var claimXpSequence: IntArray = intArrayOf(),
    var xpEvents: MutableList<Pair<Long, Int>> = mutableListOf(),
    var emaShortXpPerHour: Double = 0.0,
    var emaLongXpPerHour: Double = 0.0,
    var lastEmaUpdateMs: Long = System.currentTimeMillis(),
    /** Next reset time in epoch millis as parsed from menu. 0 if unknown. */
    var nextResetAtMs: Long = 0,
    /** The reset time we last applied so we don't double-reset. 0 if none. */
    var lastAppliedResetAtMs: Long = 0,
)

@Serializable
data class PlayerFishCollection(
    val records: MutableList<FishRecordState> = mutableListOf()
)

@Serializable
data class FishRecordState(
    val fishName: String,
    val climate: String,
    val collection: String,
    val rarity: Rarity,
    val catchTime: String = "ALWAYS",
    val caughtWeights: List<String> = emptyList(),
    val elusive: Boolean = false
)

@Serializable
data class ExchangeState(
    var activeVersionMs: Long = 0,
    var soldVersionMs: Long = 0,
    var activeListings: MutableList<McciApi.IslandExchangeListing> = mutableListOf(),
    var soldListings: MutableList<McciApi.IslandExchangeListing> = mutableListOf()
)

object PlayerStateIO {
    // configDir/trident/playerstate.json
    private val path: Path = FabricLoader.getInstance()
        .configDir
        .resolve("islandplusplus")
        .resolve("playerstate.json")

    private val json = Json { prettyPrint = true }

    fun save() {
        val serializable = TridentClient.playerState

        Files.createDirectories(path.parent)

        val text = json.encodeToString(serializable)

        val tmp = path.resolveSibling("${path.fileName}.tmp")
        Files.writeString(
            tmp,
            text,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun load(): PlayerState {
        ChatUtils.info("Loading player state from $path")
        if (!Files.exists(path)) return PlayerState()
        val text = Files.readString(path)
        val serializable = json.decodeFromString<PlayerState>(text)
        return serializable
    }
}