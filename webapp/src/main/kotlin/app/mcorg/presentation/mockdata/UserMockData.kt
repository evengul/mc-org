package app.mcorg.presentation.mockdata

object UserMockData {
    data class MockUser(
        val id: Int,
        val name: String
    )

    val Alex = MockUser(1, "Alex")
    val Steve = MockUser(2, "Steve")
    val Creeper = MockUser(3, "Creeper")
    val RedstoneWizard = MockUser(4, "RedstoneWizard")
    val VillagerMaster = MockUser(5, "VillagerMaster")
    val IronMiner = MockUser(6, "IronMiner")
    val XPGrinder = MockUser(7, "XPGrinder")
    val FarmingPro = MockUser(8, "FarmingPro")
    val OrganizedMiner = MockUser(9, "OrganizedMiner")
    val SlimeBall = MockUser(10, "SlimeBall")
    val BoomMaster = MockUser(11, "BoomMaster")
    val VerticalMover = MockUser(12, "VerticalMover")
    val SortingWizard = MockUser(13, "SortingWizard")

    val allUsers = listOf(Alex, Steve, Creeper, RedstoneWizard, VillagerMaster, IronMiner, XPGrinder, FarmingPro, OrganizedMiner, SlimeBall, BoomMaster, VerticalMover, SortingWizard)
}