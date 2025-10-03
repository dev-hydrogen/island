package cc.pe3epwithyou.trident.feature.fishing

import cc.pe3epwithyou.trident.client.TridentClient
import cc.pe3epwithyou.trident.net.McciApi
import cc.pe3epwithyou.trident.utils.ChatUtils
import cc.pe3epwithyou.trident.state.FishRecordState
import cc.pe3epwithyou.trident.state.PlayerFishCollection
import cc.pe3epwithyou.trident.state.PlayerStateIO
import cc.pe3epwithyou.trident.state.Rarity
import net.minecraft.network.chat.HoverEvent.Action
import net.minecraft.network.chat.HoverEvent.ShowText
import java.util.concurrent.Executors

object FishCollectionService {
    private val executor = Executors.newSingleThreadExecutor()

    /**
     * Merge a list of records into the player's collection additively.
     * Never removes existing records; union caught weights and prefer richer metadata.
     */
    private fun mergeAdditive(records: List<FishRecordState>) {
        val ps = TridentClient.playerState
        val existing = ps.fishCollection.records
        fun keyOf(r: FishRecordState): String = (r.collection.trim().lowercase() + "::" + r.fishName.trim().lowercase())

        val map: MutableMap<String, FishRecordState> = existing.associateBy { keyOf(it) }.toMutableMap()

        records.forEach { incoming ->
            val k = keyOf(incoming)
            val prev = map[k]
            if (prev == null) {
                map[k] = incoming.copy(
                    caughtWeights = incoming.caughtWeights.map { it.uppercase() }.distinct()
                )
            } else {
                // Merge fields: prefer non-UNKNOWN climate, preserve earliest non-default catchTime
                val mergedClimate = if (prev.climate.equals("UNKNOWN", true) && !incoming.climate.equals("UNKNOWN", true)) incoming.climate else prev.climate
                val mergedCatchTime = when {
                    prev.catchTime.equals("ALWAYS", true) && !incoming.catchTime.equals("ALWAYS", true) -> incoming.catchTime
                    else -> prev.catchTime
                }
                // Prefer highest rarity if they differ
                fun rarityRank(r: Rarity): Int = when (r) {
                    Rarity.COMMON -> 0
                    Rarity.UNCOMMON -> 1
                    Rarity.RARE -> 2
                    Rarity.EPIC -> 3
                    Rarity.LEGENDARY -> 4
                    Rarity.MYTHIC -> 5
                }
                val mergedRarity = if (rarityRank(incoming.rarity) > rarityRank(prev.rarity)) incoming.rarity else prev.rarity
                // Union weights (uppercased)
                val mergedWeights = (prev.caughtWeights.map { it.uppercase() } + incoming.caughtWeights.map { it.uppercase() })
                    .distinct()
                val mergedElusive = prev.elusive || incoming.elusive
                map[k] = prev.copy(
                    climate = mergedClimate,
                    catchTime = mergedCatchTime,
                    rarity = mergedRarity,
                    caughtWeights = mergedWeights,
                    elusive = mergedElusive
                )
            }
        }

        // Write back preserving determinism
        ps.fishCollection.records.clear()
        ps.fishCollection.records.addAll(map.values.sortedWith(compareBy({ it.collection.lowercase() }, { it.fishName.lowercase() })))
        PlayerStateIO.save()
        cc.pe3epwithyou.trident.interfaces.DialogCollection.refreshDialog("fishcollection")
    }

    /** Add or update a single record additively. */
    fun mergeAdditive(record: FishRecordState) = mergeAdditive(listOf(record))

    fun resetCollection() {
        TridentClient.playerState.fishCollection.records.clear()
        PlayerStateIO.save()
        cc.pe3epwithyou.trident.interfaces.DialogCollection.refreshDialog("fishcollection")
    }

    fun refreshForPlayer(uuid: String): Boolean {
        executor.submit {
            try {
                val resp = McciApi.fetchFishCollection(uuid)
                val records = resp.data?.player?.collections?.fish ?: emptyList()
                val mapped = records.mapNotNull { rec ->
                    val f = rec.fish ?: return@mapNotNull null
                    FishRecordState(
                        fishName = f.name,
                        climate = f.climate,
                        collection = f.collection,
                        rarity = rarityFrom(f.rarity),
                        catchTime = (f.catchTime ?: "ALWAYS"),
                        caughtWeights = rec.weights.map { it.weight },
                        elusive = (f.elusive == true)
                    )
                }
                // Merge additively to avoid losing local detections
                mergeAdditive(mapped)
            } catch (err: Throwable) {
                ChatUtils.debugLog("Failed to fetch fishing collection for player ${uuid}: ${err}")
                // ignore for now or log if needed
            }
        }
        // Indicate that the fetch was scheduled
        return true
    }

    private fun rarityFrom(s: String): Rarity = when (s.lowercase()) {
        "common" -> Rarity.COMMON
        "uncommon" -> Rarity.UNCOMMON
        "rare" -> Rarity.RARE
        "epic" -> Rarity.EPIC
        "legendary" -> Rarity.LEGENDARY
        "mythic" -> Rarity.MYTHIC
        else -> Rarity.COMMON
    }

    /**
     * Attempt to extract fish info from a chat hover component and merge immediately.
     * Best-effort: if some fields are missing, fill with sensible defaults (additive only).
     */
    fun noteCatchFromChatHover(message: net.minecraft.network.chat.Component) {
        try {
            val flat = message.toFlatList()
            var hoverText: net.minecraft.network.chat.Component? = null
            flat.forEach { part ->
                val style = part.style
                val he = style.hoverEvent ?: return@forEach
                val act = he.action()
                if (act == Action.SHOW_TEXT) {
                    val v: ShowText = he as? ShowText ?: return@forEach
                    hoverText = v.value()
                    return@forEach
                }
            }
            val text = hoverText?.string ?: return
            val lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n")

            fun find(re: Regex): String? = lines.firstNotNullOfOrNull { l -> re.find(l)?.groupValues?.getOrNull(1) }

            val weight = find(Regex("(?i)Weight:\\s*([A-Za-z]+)"))?.uppercase()
            val time = find(Regex("(?i)Time:\\s*(Always|Day|Night)"))?.uppercase() ?: "ALWAYS"
            val type = find(Regex("(?i)Type:\\s*(Normal|Elusive)"))
            val island = find(Regex("(?i)Island:\\s*([^\\n\\r]+)")) ?: (TridentClient.playerState.currentCollection ?: "Unknown Island")

            // Try to infer the fish name from the main message inside brackets [Fish Name]
            val nameInMsg = Regex("You caught:\\s*\\[(.+?)]").find(message.string)?.groupValues?.getOrNull(1)
            val fishName = nameInMsg ?: return

            val elusive = type?.equals("Elusive", true) == true

            // Try to detect rarity from any subcomponent that uses a font path (best-effort)
            var rarity: Rarity = Rarity.COMMON
            run {
                flat.forEach { p ->
                    val font = p.style.font
                    if (font != null && font.namespace == "mcc") {
                        val path = font.path
                        // Heuristic: map known rarity icon paths if present
                        rarity = when {
                            path.contains("legendary", true) -> Rarity.LEGENDARY
                            path.contains("mythic", true) -> Rarity.MYTHIC
                            path.contains("epic", true) -> Rarity.EPIC
                            path.contains("rare", true) -> Rarity.RARE
                            path.contains("uncommon", true) -> Rarity.UNCOMMON
                            else -> rarity
                        }
                    }
                }
            }

            val record = FishRecordState(
                fishName = fishName,
                climate = "UNKNOWN",
                collection = island,
                rarity = rarity,
                catchTime = time,
                caughtWeights = if (weight != null) listOf(weight) else emptyList(),
                elusive = elusive
            )
            mergeAdditive(record)
        } catch (err: Throwable) {
            ChatUtils.debugLog("Failed to parse catch from chat hover: ${err}")
            // ignore parsing failure; additive policy means we only act on confident detections
        }
    }
}


