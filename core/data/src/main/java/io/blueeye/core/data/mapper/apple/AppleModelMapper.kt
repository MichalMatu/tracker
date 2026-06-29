package io.blueeye.core.data.mapper.apple

object AppleModelMapper {
    private val mapping =
        mapOf(
            // === iPHONES ===
            "iPhone16,2" to "iPhone 15 Pro Max",
            "iPhone16,1" to "iPhone 15 Pro",
            "iPhone15,5" to "iPhone 15 Plus",
            "iPhone15,4" to "iPhone 15",
            "iPhone15,3" to "iPhone 14 Pro Max",
            "iPhone15,2" to "iPhone 14 Pro",
            "iPhone14,8" to "iPhone 14 Plus",
            "iPhone14,7" to "iPhone 14",
            "iPhone14,6" to "iPhone SE (3rd gen)",
            "iPhone14,5" to "iPhone 13",
            "iPhone14,4" to "iPhone 13 mini",
            "iPhone14,3" to "iPhone 13 Pro Max",
            "iPhone14,2" to "iPhone 13 Pro",
            "iPhone13,4" to "iPhone 12 Pro Max",
            "iPhone13,3" to "iPhone 12 Pro",
            "iPhone13,2" to "iPhone 12",
            "iPhone13,1" to "iPhone 12 mini",
            "iPhone12,8" to "iPhone SE (2nd gen)",
            "iPhone12,5" to "iPhone 11 Pro Max",
            "iPhone12,3" to "iPhone 11 Pro",
            "iPhone12,1" to "iPhone 11",
            "iPhone11,8" to "iPhone XR",
            "iPhone11,6" to "iPhone XS Max",
            "iPhone11,2" to "iPhone XS",
            "iPhone10,6" to "iPhone X",
            "iPhone10,3" to "iPhone X",
            // === iPADS ===
            "iPad15,7" to "iPad Pro 13-inch (M4)",
            "iPad15,8" to "iPad Pro 13-inch (M4)",
            "iPad15,3" to "iPad Pro 11-inch (M4)",
            "iPad15,4" to "iPad Pro 11-inch (M4)",
            "iPad14,10" to "iPad Air 13-inch (M2)",
            "iPad14,11" to "iPad Air 13-inch (M2)",
            "iPad14,8" to "iPad Air 11-inch (M2)",
            "iPad14,9" to "iPad Air 11-inch (M2)",
            "iPad13,18" to "iPad (10th gen)",
            "iPad13,19" to "iPad (10th gen)",
            "iPad13,16" to "iPad Air (5th gen)",
            "iPad13,17" to "iPad Air (5th gen)",
            "iPad14,6" to "iPad Pro 12.9-inch (6th gen)",
            "iPad14,5" to "iPad Pro 12.9-inch (6th gen)",
            "iPad14,4" to "iPad Pro 11-inch (4th gen)",
            "iPad14,3" to "iPad Pro 11-inch (4th gen)",
            "iPad13,10" to "iPad Pro 12.9-inch (5th gen)",
            "iPad13,8" to "iPad Pro 12.9-inch (5th gen)",
            // === MACS ===
            "MacBookAir10,1" to "MacBook Air (M1, 2020)",
            "MacBookAir14,2" to "MacBook Air (13-inch, M2, 2022)",
            "MacBookAir14,15" to "MacBook Air (15-inch, M2, 2023)",
            "MacBookPro17,1" to "MacBook Pro (13-inch, M1, 2020)",
            "MacBookPro18,3" to "MacBook Pro (14-inch, M1 Pro/Max, 2021)",
            "MacBookPro18,4" to "MacBook Pro (14-inch, M1 Pro/Max, 2021)",
            "Mac13,1" to "Mac Studio (M1 Max, 2022)",
            "Mac13,2" to "Mac Studio (M1 Ultra, 2022)",
            "Mac14,2" to "MacBook Air (M2, 2022)",
            "Mac14,3" to "Mac mini (M2, 2023)",
            "Mac14,12" to "Mac mini (M2 Pro, 2023)",
            // === WATCHES ===
            "Watch1,1" to "Apple Watch (1st gen) 38mm",
            "Watch1,2" to "Apple Watch (1st gen) 42mm",
            "Watch2,6" to "Apple Watch Series 1 38mm",
            "Watch2,7" to "Apple Watch Series 1 42mm",
            "Watch2,3" to "Apple Watch Series 2 38mm",
            "Watch2,4" to "Apple Watch Series 2 42mm",
            "Watch3,1" to "Apple Watch Series 3 38mm (Cellular)",
            "Watch3,2" to "Apple Watch Series 3 42mm (Cellular)",
            "Watch3,3" to "Apple Watch Series 3 38mm",
            "Watch3,4" to "Apple Watch Series 3 42mm",
            "Watch4,1" to "Apple Watch Series 4 40mm",
            "Watch4,2" to "Apple Watch Series 4 44mm",
            "Watch4,3" to "Apple Watch Series 4 40mm (Cellular)",
            "Watch4,4" to "Apple Watch Series 4 44mm (Cellular)",
            "Watch5,1" to "Apple Watch Series 5 40mm",
            "Watch5,2" to "Apple Watch Series 5 44mm",
            "Watch5,3" to "Apple Watch Series 5 40mm (Cellular)",
            "Watch5,4" to "Apple Watch Series 5 44mm (Cellular)",
            "Watch5,9" to "Apple Watch SE 40mm",
            "Watch5,10" to "Apple Watch SE 44mm",
            "Watch5,11" to "Apple Watch SE 40mm (Cellular)",
            "Watch5,12" to "Apple Watch SE 44mm (Cellular)",
            "Watch6,1" to "Apple Watch Series 6 40mm",
            "Watch6,2" to "Apple Watch Series 6 44mm",
            "Watch6,3" to "Apple Watch Series 6 40mm (Cellular)",
            "Watch6,4" to "Apple Watch Series 6 44mm (Cellular)",
            "Watch6,6" to "Apple Watch Series 7 41mm",
            "Watch6,7" to "Apple Watch Series 7 45mm",
            "Watch6,8" to "Apple Watch Series 7 41mm (Cellular)",
            "Watch6,9" to "Apple Watch Series 7 45mm (Cellular)",
            "Watch6,10" to "Apple Watch SE (2nd gen) 40mm",
            "Watch6,11" to "Apple Watch SE (2nd gen) 44mm",
            "Watch6,12" to "Apple Watch SE (2nd gen) 40mm (Cellular)",
            "Watch6,13" to "Apple Watch SE (2nd gen) 44mm (Cellular)",
            "Watch6,14" to "Apple Watch Series 8 41mm",
            "Watch6,15" to "Apple Watch Series 8 45mm",
            "Watch6,16" to "Apple Watch Series 8 41mm (Cellular)",
            "Watch6,17" to "Apple Watch Series 8 45mm (Cellular)",
            "Watch6,18" to "Apple Watch Ultra",
            "Watch7,1" to "Apple Watch Series 9 41mm",
            "Watch7,2" to "Apple Watch Series 9 45mm",
            "Watch7,3" to "Apple Watch Series 9 41mm (Cellular)",
            "Watch7,4" to "Apple Watch Series 9 45mm (Cellular)",
            "Watch7,5" to "Apple Watch Ultra 2",
        )

    fun getMarketingName(model: String): String? {
        // Try exact match
        mapping[model]?.let {
            return it
        }

        // Try fuzzy for Macs (often Mac14,2 etc)
        // Or if it's generic
        return null
    }

    fun isAppleDevice(manufacturer: String?): Boolean {
        return manufacturer?.contains("Apple", ignoreCase = true) == true
    }
}
