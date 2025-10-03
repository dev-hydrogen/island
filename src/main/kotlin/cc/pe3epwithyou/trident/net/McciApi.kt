package cc.pe3epwithyou.trident.net

import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import cc.pe3epwithyou.trident.config.Config
import cc.pe3epwithyou.trident.utils.ChatUtils.debugLog
import cc.pe3epwithyou.trident.utils.ChatUtils

object McciApi {
    private val http = HttpClient.newBuilder().build()
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Low-level POST to the GraphQL endpoint.
     */
    fun postGraphql(body: String): String {
        val apiKey = Config.Api.apiKey
        //ChatUtils.debugLog("MCCI API -> POST /graphql apiKey='${apiKey}'")
        ChatUtils.debugLog("MCCI API -> POST /graphql apiKey='[REDACTED]'")
        val userAgent = Config.Api.userAgent
        ChatUtils.debugLog("MCCI API -> POST /graphql ua='${userAgent}', bodyLen=${body.length}")
        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.mccisland.net/graphql"))
            .header("Content-Type", "application/json")
            .header("X-API-Key", apiKey)
            .header("User-Agent", userAgent)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        val rlRemain = res.headers().firstValue("X-Ratelimit").orElse("")
        ChatUtils.debugLog("MCCI API <- ${res.statusCode()} ratelimit remaining='${rlRemain}' bodyLen=${res.body()?.length ?: 0}")
        
        if (res.statusCode() !in 200..299) error("MCCI API ${res.statusCode()}: ${res.body()}")
        return res.body()
    }

    /**
     * Build a JSON variables object from pairs.
     */
    fun variablesOf(vararg pairs: Pair<String, JsonElement>): JsonObject = buildJsonObject {
        for ((k, v) in pairs) put(k, v)
    }

    /**
     * Encode an arbitrary serializable value into a JsonElement for variables.
     */
    fun <V> toJsonElement(value: V, serializer: KSerializer<V>): JsonElement = json.encodeToJsonElement(serializer, value)

    @Serializable
    data class GraphqlError(val message: String)

    @Serializable
    data class GraphqlEnvelope<T>(val data: T? = null, val errors: List<GraphqlError>? = null)

    @Serializable
    data class GraphqlRequest(
        val query: String,
        val variables: JsonObject = buildJsonObject { },
        val operationName: String? = null,
    )

    /**
     * Execute a GraphQL request and decode the `data` field to T.
     * Throws an error if the response contains GraphQL errors or missing data.
     */
    fun <T> executeData(
        query: String,
        variables: JsonObject = buildJsonObject { },
        operationName: String? = null,
        dataSerializer: KSerializer<T>
    ): T {
        val payload = json.encodeToString(GraphqlRequest.serializer(), GraphqlRequest(query, variables, operationName))
        val body = postGraphql(payload)
        val envelope = json.decodeFromString(GraphqlEnvelope.serializer(dataSerializer), body)
        if (!envelope.errors.isNullOrEmpty()) {
            val msg = envelope.errors.joinToString("; ") { it.message }
            error("GraphQL errors: ${msg}")
        }
        return envelope.data ?: error("GraphQL response missing data")
    }

    @Serializable
    data class FishCollectionResponse(val data: Data?) {
        @Serializable
        data class Data(val player: Player?) {
            @Serializable
            data class Player(val collections: Collections?) {
                @Serializable
                data class Collections(val fish: List<FishRecord> = emptyList()) {
                    @Serializable
                    data class FishRecord(
                        val fish: Fish? = null,
                        val weights: List<FishCaughtWeight> = emptyList()
                    )
                    @Serializable
                    data class Fish(
                        val name: String, // Fish Name
                        val climate: String, // "Temperate", "Tropical", "Barren"
                        val rarity: String, // "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"
                        val collection: String, // Island Name, formatted as "Island Name"
                        val catchTime: String? = null, // "ALWAYS", "DAY", "NIGHT"
                        val elusive: Boolean? = null 
                    )
                    @Serializable
                    data class FishCaughtWeight(
                        val weight: String, // "AVERAGE", "LARGE", "MASSIVE", "GARGANTUAN", for crabs "AVERAGE", "LARGE", "COLOSSAL"
                        val firstCaught: String? = null
                    )
                }
            }
        }
    }

    fun fetchFishCollection(uuid: String): FishCollectionResponse {
        if (Config.Debug.enableLogging) ChatUtils.debugLog("Fetching fish collection for uuid=${uuid}")
        val query = """
            query PlayerFish(${ '$' }uuid: UUID!) {
              player(uuid: ${ '$' }uuid) {
                collections {
                  fish {
                    fish {
                      name
                      climate
                      collection
                      rarity
                      catchTime
                      elusive
                    }
                    weights {
                      weight
                      firstCaught
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val variables = variablesOf("uuid" to JsonPrimitive(uuid))
        val data = executeData(query, variables, operationName = "PlayerFish", dataSerializer = FishCollectionResponse.Data.serializer())
        val decoded = FishCollectionResponse(data)
        ChatUtils.debugLog("Fish collection fetched: decoded=${decoded}")
        val count = decoded.data?.player?.collections?.fish?.size ?: 0
        ChatUtils.debugLog("Fish collection fetched: records=${count}")
        
        return decoded
    }

    /**
     * Fetch the latest GraphQL SDL schema text from the public endpoint.
     * This can be used to keep client types up-to-date externally.
     */
    fun fetchSchemaSdl(): String {
        val userAgent = Config.Api.userAgent
        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.mccisland.net/graphql/schema"))
            .header("User-Agent", userAgent)
            .GET()
            .build()
        val res = http.send(req, HttpResponse.BodyHandlers.ofString())
        if (res.statusCode() !in 200..299) error("MCCI schema ${res.statusCode()}: ${res.body()}")
        return res.body()
    }

    // -------- Island Exchange --------

    @Serializable
    data class IslandExchangeListing(
        val asset: AssetSummary,
        val amount: Int,
        val creationTime: String,
        val endTime: String,
        val cost: Int,
        val identifier: String,
    ) {
        @Serializable
        data class AssetSummary(
            val name: String,
            val rarity: String,
            val uniqueIdentifier: String,
        )
    }

    @Serializable
    data class ActiveIslandExchangeData(
        val activeIslandExchangeListings: List<IslandExchangeListing> = emptyList(),
    )

    @Serializable
    data class SoldIslandExchangeData(
        val soldIslandExchangeListings: List<IslandExchangeListing> = emptyList(),
    )

    fun fetchActiveIslandExchangeListings(): List<IslandExchangeListing> {
        if (Config.Debug.enableLogging) ChatUtils.debugLog("Fetching active Island Exchange listings")
        val query = """
            query ActiveListings {
              activeIslandExchangeListings {
                asset { name rarity uniqueIdentifier }
                amount
                creationTime
                endTime
                cost
                identifier
              }
            }
        """.trimIndent()
        val data = executeData(
            query = query,
            variables = buildJsonObject { },
            operationName = "ActiveListings",
            dataSerializer = ActiveIslandExchangeData.serializer()
        )
        val count = data.activeIslandExchangeListings.size
        ChatUtils.debugLog("Active Island Exchange listings fetched: count=${count}")
        return data.activeIslandExchangeListings
    }

    fun fetchSoldIslandExchangeListings(): List<IslandExchangeListing> {
        if (Config.Debug.enableLogging) ChatUtils.debugLog("Fetching sold Island Exchange listings (last 24h)")
        val query = """
            query SoldListings {
              soldIslandExchangeListings {
                asset { name rarity uniqueIdentifier }
                amount
                creationTime
                endTime
                cost
                identifier
              }
            }
        """.trimIndent()
        val data = executeData(
            query = query,
            variables = buildJsonObject { },
            operationName = "SoldListings",
            dataSerializer = SoldIslandExchangeData.serializer()
        )
        val count = data.soldIslandExchangeListings.size
        ChatUtils.debugLog("Sold Island Exchange listings fetched: count=${count}")
        return data.soldIslandExchangeListings
    }
}


