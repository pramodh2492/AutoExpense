package com.expensetracker.data.local

import com.expensetracker.data.model.TransactionCategory

object KnownMerchants {

    // Returns category if merchant name or SMS body matches a known pattern, null otherwise
    fun categorize(merchant: String, smsBody: String = ""): TransactionCategory? {
        val lower = merchant.lowercase().trim()
        val bodyLower = smsBody.lowercase().trim()

        // Check exact/partial matches against known databases using merchant name
        for ((keywords, category) in merchantDatabase) {
            if (keywords.any { lower.contains(it) }) {
                return category
            }
        }

        // If merchant didn't match, check SMS body for keyword matches
        // This catches cases where merchant extraction failed (e.g., "Unknown")
        if (bodyLower.isNotEmpty()) {
            for ((keywords, category) in merchantDatabase) {
                if (keywords.any { bodyLower.contains(it) }) {
                    return category
                }
            }
        }

        // Check naming patterns (suffix/prefix based) against merchant name
        for ((pattern, category) in namingPatterns) {
            if (pattern.containsMatchIn(lower)) {
                return category
            }
        }

        return null
    }

    // Grocery stores — chains, local Chennai stores, patterns
    private val groceryKeywords = listOf(
        // National chains
        "bigbasket", "blinkit", "zepto", "jiomart", "grofers", "dunzo daily",
        "dmart", "d-mart", "reliance fresh", "reliance smart", "more megastore",
        "more supermarket", "spencer", "star bazaar", "hypercity", "easyday",
        "nature basket", "godrej nature", "nilgiris", "heritage fresh",

        // Chennai specific grocery/provision stores
        "sri hari krishna", "grace mart", "nut and spice", "nuts and spice", "nuts n spices",
        "pazhamudir nilayam", "pazhamudir", "chennai grocery",
        "saravana stores", "saravana store", "the chennai store",
        "kovai pazhamudir", "pazhamudir cholai",
        "horticorp", "namma kadai", "namma store",
        "rathna stores", "ratna stores",
        "murugan stores", "murugan store",
        "lakshmi stores", "lakshmi store",
        "krishna stores", "krishna store",
        "ganesh stores", "ganesh store",
        "sri balaji store", "balaji store",
        "amman stores", "amman store",
        "velavan stores", "velavan store",
        "pazhamudir nilayam", "fruits shop",

        // Common keywords
        "provision", "kirana", "grocery", "supermarket", "hypermarket",
        "vegetables", "fruits", "organic", "fresh market",
        "dairy", "milk booth", "aavin", "amul", "mother dairy",
        "rice", "oil store", "masala", "spices",
        "wholesale", "departmental",

        // Vegetable/fruit markets
        "koyambedu", "thiruvanmiyur market", "besant nagar market",
    )

    // Restaurants, food, cafes, bakeries
    private val foodKeywords = listOf(
        // National chains
        "swiggy", "zomato", "dominos", "pizza hut", "mcdonalds", "kfc",
        "burger king", "subway", "starbucks", "cafe coffee day", "ccd",
        "barista", "dunkin", "baskin robbins", "naturals ice cream",
        "haldiram", "barbeque nation", "absolute barbecue", "ab's",
        "paradise biryani", "behrouz", "faasos", "eatfit", "box8",
        "licious", "freshmenu", "rebel foods", "chai point",

        // Chennai specific restaurants & chains
        "mangifera", "ekayars", "sri sai dosa",
        "saravana bhavan", "sangeetha restaurant", "sangeetha veg",
        "adyar ananda bhavan", "a2b", "ananda bhavan",
        "hot chips", "junior kuppanna", "kuppanna",
        "murugan idli", "murugan idli shop",
        "ratna cafe", "rayar's mess", "rayars mess",
        "buhari", "buhari hotel", "ponnusamy",
        "dindigul thalappakatti", "thalappakatti",
        "anjappar", "chettinad restaurant",
        "palmshore", "residency towers",
        "cream stone", "ibaco", "amadora",
        "french loaf", "hot breads", "cakes and bakes",
        "magnolia", "browns", "zuka",
        "sukkubhai biryani", "star biryani", "ambur biryani",
        "madras filter coffee", "kumbakonam degree coffee",
        "chai kings", "tea junction", "chai tapri",
        "sree annapoorna", "annapoorna",
        "vasanta bhavan", "vasanta bhawan",
        "mathsya", "dakshin", "benjarong",
        "copper chimney", "mainland china", "absolutely",
        "nair mess", "military hotel",
        "krishna sweets", "grand sweets", "sri krishna sweets",
        "adyar bakery", "amma naana",
        "salem rr", "dindigul",
        "madurai pandian", "pandian mess",
        "erode sree mahaa",

        // Common food keywords
        "restaurant", "cafe", "bakery", "biryani", "pizza",
        "burger", "chicken", "shawarma", "rolls", "dosa",
        "idli", "meals", "tiffin", "mess", "bhavan", "bhawan",
        "hotel", "dhaba", "canteen", "juice", "lassi",
        "tea stall", "coffee shop", "sweet", "snack",
        "ice cream", "dessert", "cake", "pastry",
    )

    // Transport
    private val transportKeywords = listOf(
        "uber", "ola", "rapido", "namma yatri",
        "metro", "chennai metro", "cmrl",
        "irctc", "redbus", "abhibus",
        "mtc", "bus pass", "parking",
        "fastag", "toll", "toll plaza",
        "bike taxi", "auto ", "autorickshaw",
        "yulu", "bounce", "vogo",
    )

    // Shopping
    private val shoppingKeywords = listOf(
        "amazon", "flipkart", "myntra", "meesho", "ajio", "nykaa",
        "croma", "reliance digital", "vijay sales", "poorvika",
        "sangeetha mobiles", "lot mobiles", "big c mobiles",
        "saravana selvarathinam", "grt", "tanishq", "kalyan jewellers",
        "joyalukkas", "malabar gold", "lalitha jewellery",
        "pothys", "rmkv", "nalli", "nalli silks", "kumaran silks",
        "chennai silks", "the chennai silks", "jayalakshmi silks",
        "max fashion", "trends", "lifestyle", "westside", "zara",
        "h&m", "uniqlo", "decathlon", "skechers", "nike", "adidas",
        "lenskart", "titan eye", "coolwinks",
        "ikea", "hometown", "urban ladder", "pepperfry",
        "chroma", "croma", "bajaj electronics",
        "reliance trends", "brand factory",
    )

    // Bills & utilities
    private val billsKeywords = listOf(
        "electricity", "tangedco", "tneb", "bescom",
        "water", "metrowater", "cmwssb",
        "gas", "hp gas", "bharat gas", "indane",
        "piped gas", "adani gas", "mahanagar gas", "igs",
        "broadband", "act fibernet", "airtel xstream",
        "jio fiber", "jio", "airtel", "vi ", "bsnl", "vodafone",
        "wifi", "internet", "dth", "tata play", "tata sky",
        "dish tv", "sun direct", "d2h",
        "rent", "house rent", "maintenance",
        "society", "apartment", "flat maintenance",
        "insurance", "insurance premium", "lic", "star health", "hdfc ergo",
        "policy bazaar", "policybazaar", "digit insurance", "acko",
        "policy", "premium", "tp ach hdf", "hdfc life", "icici pru",
        "bajaj allianz", "max life", "sbi life", "tata aia",
        "airtel mobile", "mobile recharge",
        "bill payment", "recharge",
    )

    // Entertainment
    private val entertainmentKeywords = listOf(
        "netflix", "hotstar", "disney", "prime video", "amazon prime",
        "spotify", "youtube premium", "apple music", "gaana",
        "zee5", "sonyliv", "mxplayer", "jiocinema", "voot",
        "pvr", "inox", "sathyam cinemas", "rohini theatre",
        "devi theatre", "kamala theatre", "udhayam theatre",
        "bookmyshow", "paytm insider", "ticketnew",
        "wonderla", "vgp", "queensland", "kishkinta",
        "gaming", "playstation", "xbox", "steam",
    )

    // Health
    private val healthKeywords = listOf(
        "hospital", "apollo hospital", "fortis", "max health",
        "kauvery", "miot", "vijaya hospital", "sims",
        "pharmacy", "medplus", "apollo pharmacy", "netmeds",
        "1mg", "pharmeasy", "frank ross", "wellness forever",
        "practo", "doctor", "clinic", "dental",
        "lab", "diagnostic", "thyrocare", "dr lal path",
        "srl diagnostics", "metropolis",
        "optical",
        "gym", "cult.fit", "fitness",
    )

    // Education
    private val educationKeywords = listOf(
        "school", "college", "university", "institute",
        "tuition", "coaching", "academy",
        "udemy", "coursera", "unacademy", "byju", "upgrad",
        "skillshare", "linkedin learning",
        "books", "stationery", "sapna book", "crossword",
        "landmark", "higginbothams",
    )

    // Savings & Investments
    private val savingsKeywords = listOf(
        "groww", "zerodha", "kuvera", "coin by zerodha",
        "smallcase", "etmoney", "paytm money", "angel one",
        "upstox", "5paisa", "motilal oswal", "icicidirect",
        "sbi mutual fund", "hdfc mutual fund", "axis mutual fund",
        "nippon india", "icici prudential", "kotak mahindra mf",
        "dsp mutual fund", "tata mutual fund", "franklin templeton",
        "sip", "mutual fund", "mf purchase",
        "ppf", "nps", "national pension",
        "fixed deposit", "recurring deposit",
        "sovereign gold bond", "sgb",
        "investment", "stock", "share",
    )

    // Fuel
    private val fuelKeywords = listOf(
        "petrol", "diesel", "fuel station",
        "hp pump", "hp petrol", "hindustan petroleum",
        "iocl", "indian oil", "bharat petroleum", "bpcl",
        "shell", "nayara", "essar",
        "ev charging", "ather", "charging station",
    )

    // Travel
    private val travelKeywords = listOf(
        "makemytrip", "goibibo", "cleartrip", "yatra",
        "easemytrip", "ixigo", "via.com",
        "oyo", "treebo", "fabhotel", "airbnb",
        "booking.com", "agoda", "trivago",
        "indigo", "spicejet", "air india", "vistara",
        "akasa", "flight", "airline",
        "taj hotel", "itc hotel", "marriott", "hyatt",
    )

    // The main database: keywords → category
    // Order matters — first match wins. Travel before food so specific hotel brands
    // (Taj Hotel, Marriott) match before generic "hotel" in food keywords.
    private val merchantDatabase = listOf(
        groceryKeywords to TransactionCategory.GROCERIES,
        travelKeywords to TransactionCategory.TRAVEL,
        foodKeywords to TransactionCategory.FOOD_DINING,
        transportKeywords to TransactionCategory.TRANSPORT,
        shoppingKeywords to TransactionCategory.SHOPPING,
        billsKeywords to TransactionCategory.BILLS_UTILITIES,
        entertainmentKeywords to TransactionCategory.ENTERTAINMENT,
        healthKeywords to TransactionCategory.HEALTH,
        educationKeywords to TransactionCategory.EDUCATION,
        savingsKeywords to TransactionCategory.SAVINGS,
        fuelKeywords to TransactionCategory.FUEL,
    )

    // Naming pattern regex — catches local shop naming conventions
    private val namingPatterns = listOf(
        // Grocery naming patterns
        Regex("""\b(mart|store|stores|traders|trading|provision|kirana)\b""") to TransactionCategory.GROCERIES,
        Regex("""\b(vegetables|veggies|fruits|greens|organic)\b""") to TransactionCategory.GROCERIES,
        Regex("""\b(supermarket|hypermarket|departmental)\b""") to TransactionCategory.GROCERIES,
        Regex("""\b(oil|rice|flour|atta|dal)\s*(mill|store|shop|centre)""") to TransactionCategory.GROCERIES,

        // Food naming patterns
        Regex("""\b(restaurant|restro|cafe|cafeteria|eatery|bistro)\b""") to TransactionCategory.FOOD_DINING,
        Regex("""\b(biryani|meals|tiffin|dosa|idli|mess)\b""") to TransactionCategory.FOOD_DINING,
        Regex("""\b(bakery|bakers|sweets|confectionery)\b""") to TransactionCategory.FOOD_DINING,
        Regex("""\b(juice|lassi|shake|smoothie)\b""") to TransactionCategory.FOOD_DINING,
        Regex("""\b(bhavan|bhawan|hotel)\b""") to TransactionCategory.FOOD_DINING,
        Regex("""\b(cuisines?|kitchen|foods|catering|corner)\b""") to TransactionCategory.FOOD_DINING,
        Regex("""\b(chicken|mutton|fish|meat)\s*(shop|centre|stall)""") to TransactionCategory.FOOD_DINING,

        // Transport patterns
        Regex("""\b(parking|toll|cab|taxi|auto)\b""") to TransactionCategory.TRANSPORT,

        // Shopping patterns
        Regex("""\b(silks|sarees|textiles|garments|fashion|boutique)\b""") to TransactionCategory.SHOPPING,
        Regex("""\b(jeweller|gold|diamond)\b""") to TransactionCategory.SHOPPING,
        Regex("""\b(electronics|mobiles|computers|laptops)\b""") to TransactionCategory.SHOPPING,

        // Medical patterns
        Regex("""\b(pharmacy|pharma|medical|medicals|chemist)\b""") to TransactionCategory.HEALTH,
        Regex("""\b(hospital|clinic|dental|doctor|dr\.)\b""") to TransactionCategory.HEALTH,

        // Fuel patterns
        Regex("""\b(petrol|petroleum|fuel|gas station)\b""") to TransactionCategory.FUEL,

        // Education patterns
        Regex("""\b(school|college|academy|institute|coaching|tuition)\b""") to TransactionCategory.EDUCATION,
    )
}
