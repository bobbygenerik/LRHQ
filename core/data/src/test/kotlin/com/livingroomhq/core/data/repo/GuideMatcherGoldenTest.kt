package com.livingroomhq.core.data.repo

import com.livingroomhq.core.data.model.Channel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GuideMatcherGoldenTest(
    private val caseName: String,
    private val channel: Channel,
    private val guideAliases: Map<String, Set<String>>,
    private val aliasIndex: Map<String, String>,
    private val expectedGuideId: String?,
) {

    @Test
    fun resolve() {
        val actual = GuideMatcher.resolveGuideChannelId(channel, guideAliases, aliasIndex)
        assertEquals("Case failed: $caseName", expectedGuideId, actual)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any?>> {
            val stream = GuideMatcherGoldenTest::class.java.getResourceAsStream("/guide_matcher_cases.json")
                ?: error("guide_matcher_cases.json not found")
            val arr = JSONArray(stream.bufferedReader().readText())
            return buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        arrayOf(
                            obj.getString("name"),
                            parseChannel(obj.getJSONObject("channel")),
                            parseGuideAliases(obj.getJSONObject("guideAliases")),
                            parseAliasIndex(obj.getJSONObject("aliasIndex")),
                            obj.optString("expectedGuideId").takeIf { obj.has("expectedGuideId") && !obj.isNull("expectedGuideId") },
                        ),
                    )
                }
            }
        }

        private fun parseChannel(obj: JSONObject): Channel =
            Channel(
                id = obj.getString("id"),
                number = 1,
                name = obj.getString("name"),
                group = "",
                streamUrl = "http://test/stream",
                tvgId = obj.optString("tvgId").takeIf { obj.has("tvgId") && !obj.isNull("tvgId") },
                tvgName = obj.optString("tvgName").takeIf { obj.has("tvgName") && !obj.isNull("tvgName") },
                tvgChno = obj.optString("tvgChno").takeIf { obj.has("tvgChno") && !obj.isNull("tvgChno") },
            )

        private fun parseGuideAliases(obj: JSONObject): Map<String, Set<String>> =
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val aliases = obj.getJSONArray(key)
                    put(key, buildSet {
                        for (j in 0 until aliases.length()) add(aliases.getString(j))
                    })
                }
            }

        private fun parseAliasIndex(obj: JSONObject): Map<String, String> =
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, obj.getString(key))
                }
            }
    }
}
