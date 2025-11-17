package coach.chess

import coach.model.GameResult
import coach.model.PlayerColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PgnParserTest {

    @Test
    fun singleGame_whiteWin_mapsFields() {
        val pgn = """
            [Event "Rated Blitz game"]
            [Site "https://lichess.org/abc123"]
            [Date "2025.11.16"]
            [UTCDate "2025.11.16"]
            [UTCTime "06:07:38"]
            [White "Alice"]
            [Black "Bob"]
            [WhiteElo "1500"]
            [BlackElo "1520"]
            [TimeControl "600+5"]
            [ECO "C50"]
            [Result "1-0"]

            1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. c3 Nf6 5. d4 exd4 6. e5 d5 7. exf6
            *
        """.trimIndent()

        val batch = PgnParser.buildGameBatch(
            player = "Alice",
            site = "lichess.org",
            rawPgn = pgn
        )

        assertEquals("Alice", batch.player)
        assertEquals("lichess.org", batch.site)
        assertEquals(1, batch.games.size)

        val g = batch.games.first()
        assertEquals("abc123", g.id)
        assertEquals(true, g.rated)
        assertEquals("10+5", g.timeControl) // 600 seconds -> 10 minutes
        assertEquals("blitz", g.speed) // inferred from Event string
        assertEquals(PlayerColor.WHITE, g.color)
        assertEquals(GameResult.WIN, g.result)
        assertEquals("Bob", g.opponent)
        assertEquals(1500, g.playerRating)
        assertEquals(1520, g.opponentRating)
        assertEquals("C50", g.opening)
        assertNotNull(g.startedAt)
        assertEquals(true, g.pgn.contains("1. e4 e5"))
    }

    @Test
    fun singleGame_blackLoss_perspectiveHandled() {
        val pgn = """
            [Event "Casual game"]
            [Link "https://lichess.org/zzz999"]
            [UTCDate "2025.11.17"]
            [UTCTime "01:02:03"]
            [White "Carol"]
            [Black "Alice"]
            [WhiteElo "1450"]
            [BlackElo "1480"]
            [TimeControl "180+0"]
            [Result "1-0"]

            1. d4 d5 2. c4 dxc4 3. Nf3 Nf6 4. e3 e6 5. Bxc4 *
        """.trimIndent()

        val batch = PgnParser.buildGameBatch(
            player = "Alice",
            site = "lichess.org",
            rawPgn = pgn
        )

        val g = batch.games.first()
        assertEquals("zzz999", g.id)
        assertEquals(PlayerColor.BLACK, g.color)
        assertEquals(GameResult.LOSS, g.result) // 1-0 from Black perspective -> loss
        assertEquals("Carol", g.opponent)
        assertEquals(1480, g.playerRating)
        assertEquals(1450, g.opponentRating)
        assertEquals("3+0", g.timeControl) // 180 seconds -> 3 minutes
        assertEquals("blitz", g.speed) // fallback by minutes
    }

    @Test
    fun missingMetadata_defaultsToUnknownOrNull() {
        val pgn = """
            [Event "Casual game"]
            [White "Alice"]
            [Black "Bob"]
            [Result "1/2-1/2"]

            1. Nf3 Nf6 2. g3 g6 3. Bg2 Bg7 4. O-O O-O *
        """.trimIndent()

        val batch = PgnParser.buildGameBatch(
            player = "Alice",
            rawPgn = pgn
        )

        val g = batch.games.first()
        assertEquals("unknown", g.timeControl)
        // speed might be inferred from time control; absent -> unknown
        assertEquals("unknown", g.speed)
        assertNull(g.playerRating)
        assertNull(g.opponentRating)
        assertNull(g.opening)
        assertNull(g.startedAt)
        assertEquals(GameResult.DRAW, g.result)
    }

    @Test
    fun multiGame_parsesIdsAndRatedAndIgnoresNoise() {
        val pgn = """
            [Event "Rated Rapid game"]
            [Site "https://lichess.org/g111"]
            [Date "2025.11.16"]
            [White "Alice"]
            [Black "Eve"]
            [Result "1/2-1/2"]
            [TimeControl "900+10"]

            {a comment} 1. d4 d5 2. c4 c6 3. Nf3 Nf6 *


            [Event "Casual Blitz"]
            [Link "https://lichess.org/g222"]
            [White "Mallory"]
            [Black "Alice"]
            [Result "0-1"]
            [TimeControl "180+0"]

            1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 *
        """.trimIndent()

        val batch = PgnParser.buildGameBatch(
            player = "Alice",
            site = "lichess.org",
            rawPgn = pgn
        )

        assertEquals(2, batch.games.size)
        val g1 = batch.games[0]
        val g2 = batch.games[1]

        assertEquals("g111", g1.id)
        assertEquals(true, g1.rated)
        assertEquals("15+10", g1.timeControl) // 900 -> 15

        assertEquals("g222", g2.id)
        assertEquals(false, g2.rated)
        assertEquals("3+0", g2.timeControl) // 180 -> 3

        // Colors resolved per player name
        assertEquals(PlayerColor.WHITE, g1.color)
        assertEquals(PlayerColor.BLACK, g2.color)
    }
}
