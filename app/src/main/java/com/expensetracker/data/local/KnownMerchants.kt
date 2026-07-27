package com.expensetracker.data.local

import com.expensetracker.data.model.TransactionCategory

object KnownMerchants {

    // Returns category if merchant name or SMS body matches a known pattern, null otherwise
    fun categorize(merchant: String, smsBody: String = ""): TransactionCategory? {
        val lower = normalizeForMatch(merchant)
        val bodyLower = normalizeForMatch(smsBody)

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

    // Normalize a string for keyword matching. Crucially, "&" and standalone "n"
    // are treated as "and" so "Nuts & Spices" / "Nuts N Spices" match the same
    // "nuts and spice" keyword. Also collapses whitespace and lowercases.
    private fun normalizeForMatch(text: String): String {
        return text
            .lowercase()
            .replace("&", " and ")
            .replace(Regex("""\bn\b"""), "and")     // "nuts n spices" -> "nuts and spices"
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    // Grocery stores — chains, local Chennai stores, patterns
    private val groceryKeywords = listOf(
        // National chains
        "bigbasket", "big basket", "blinkit", "zepto", "jiomart", "jio mart", "grofers", "dunzo daily",
        "dmart", "d-mart", "d mart", "avenue supermart", "reliance fresh", "reliance smart", "more megastore",
        "more supermarket", "spencer", "star bazaar", "hypercity", "easyday",
        "nature basket", "godrej nature", "nilgiris", "heritage fresh",
        "swiggy instamart", "instamart", "bbnow", "bb now", "country delight",
        "milkbasket", "milk basket", "otipy", "fraazo", "supr daily", "suprdaily",
        "licious grocery", "vijetha", "ratnadeep", "metro cash", "metro wholesale",
        "smart bazaar", "vishal mega mart", "vishal megamart", "big bazaar",
        "apna bazaar", "sabziwala", "freshtohome", "fresh to home", "waycool",

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
        "swiggy", "zomato", "dominos", "domino", "pizza hut", "mcdonalds", "mcdonald", "kfc",
        "burger king", "subway", "starbucks", "cafe coffee day", "ccd",
        "barista", "dunkin", "baskin robbins", "naturals ice cream",
        "haldiram", "barbeque nation", "absolute barbecue", "ab's",
        "paradise biryani", "behrouz", "faasos", "eatfit", "box8",
        "licious", "freshmenu", "rebel foods", "chai point", "chaayos",
        "wow momo", "wow china", "goli vada pav", "smokin joes", "la pinoz",
        "third wave coffee", "blue tokai", "theobroma", "wendys", "wendy",
        "taco bell", "carls jr", "popeyes", "chai sutta bar", "mba chaiwala",
        "eatsure", "curefit food", "keventers", "gelato", "belgian waffle",
        "the belgian waffle", "kwality walls", "cornetto", "havmor",
        "biggies burger", "burger singh", "nandos", "sbarro",
        "cake zone", "monginis", "ribbons and balloons", "just bake",
        "karachi bakery", "iyengar bakery", "bakingo", "fnp cakes",
        "bikanervala", "bikaji", "wow kulfi", "frozen bottle", "bakery",

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
        "uber", "ola", "olacabs", "ola cabs", "rapido", "namma yatri", "nammayatri",
        "metro", "chennai metro", "cmrl", "delhi metro", "dmrc", "bmrcl", "namma metro",
        "irctc", "redbus", "red bus", "abhibus", "abhi bus",
        "mtc", "bus pass", "parking", "blusmart", "blu smart",
        "fastag", "fast tag", "toll", "toll plaza", "nhai", "paytm fastag",
        "bike taxi", "auto ", "autorickshaw", "quick ride", "quickride",
        "yulu", "bounce", "vogo", "meru", "savaari", "zoomcar", "zoom car",
        "revv", "royal brothers", "freshbus", "fresh bus", "chalo", "tummoc",
        "railway", "rail ticket", "train ticket", "tsrtc", "ksrtc", "apsrtc",
        "msrtc", "gsrtc", "best bus", "dtc",
    )

    // Shopping
    private val shoppingKeywords = listOf(
        "amazon", "flipkart", "myntra", "meesho", "ajio", "nykaa", "nykaa fashion",
        "tatacliq", "tata cliq", "tata neu", "snapdeal", "shopclues", "firstcry",
        "first cry", "hopscotch", "limeroad", "lime road", "bewakoof", "urbanic",
        "croma", "reliance digital", "vijay sales", "poorvika",
        "sangeetha mobiles", "lot mobiles", "big c mobiles",
        "saravana selvarathinam", "grt", "tanishq", "kalyan jewellers",
        "joyalukkas", "malabar gold", "lalitha jewellery", "bluestone", "blue stone",
        "caratlane", "carat lane", "pc jeweller", "senco gold",
        "pothys", "rmkv", "nalli", "nalli silks", "kumaran silks",
        "chennai silks", "the chennai silks", "jayalakshmi silks",
        "max fashion", "trends", "lifestyle", "westside", "zara", "shoppers stop",
        "h&m", "uniqlo", "decathlon", "skechers", "nike", "adidas", "puma", "reebok",
        "levis", "levi", "us polo", "allen solly", "peter england", "van heusen",
        "louis philippe", "jockey", "biba", "fabindia", "fab india", "w for woman",
        "lenskart", "titan eye", "coolwinks", "specsmakers", "gkb optical",
        "ikea", "hometown", "urban ladder", "pepperfry", "nilkamal", "wakefit",
        "sleepwell", "duroflex", "the sleep company", "godrej interio",
        "bajaj electronics", "boat lifestyle", "mi store", "xiaomi",
        "oneplus", "samsung", "apple store", "imagine", "unicorn store",
        "reliance trends", "brand factory", "pantaloons", "central mall",
        "wildcraft", "american tourister", "vip bags", "safari bags",
        "the body shop", "mac cosmetics", "sephora", "sugar cosmetics",
        "mamaearth", "purplle", "wow skin", "minimalist",
    )

    // Bills & utilities
    private val billsKeywords = listOf(
        "electricity", "tangedco", "tneb", "bescom",
        "water", "metrowater", "cmwssb",
        "gas", "hp gas", "bharat gas", "indane",
        "piped gas", "adani gas", "mahanagar gas", "igs",
        "broadband", "act fibernet", "act fiber", "airtel xstream", "hathway",
        "jio fiber", "jiofiber", "jio", "airtel", "vi ", "bsnl", "vodafone",
        "excitel", "spectra", "you broadband", "railwire", "rail wire", "asianet",
        "wifi", "internet", "dth", "tata play", "tata sky",
        "dish tv", "sun direct", "d2h", "videocon d2h",
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
        "spotify", "youtube premium", "apple music", "gaana", "wynk", "jiosaavn", "saavn",
        "zee5", "sonyliv", "sony liv", "mxplayer", "mx player", "jiocinema", "jio cinema", "voot",
        "aha video", "sun nxt", "sunnxt", "hoichoi", "erosnow", "eros now", "lionsgate play",
        "pvr", "inox", "pvr inox", "cinepolis", "carnival cinemas", "miraj cinemas",
        "sathyam cinemas", "rohini theatre", "ags cinemas", "escape cinemas",
        "devi theatre", "kamala theatre", "udhayam theatre",
        "bookmyshow", "book my show", "paytm insider", "ticketnew",
        "wonderla", "vgp", "queensland", "kishkinta", "imagicaa", "essel world",
        "gaming", "playstation", "xbox", "steam", "epic games", "nintendo",
        "google play games", "garena", "dream11", "dream 11", "mpl", "rummy",
    )

    // Health
    private val healthKeywords = listOf(
        "hospital", "apollo hospital", "fortis", "max health", "manipal",
        "kauvery", "miot", "vijaya hospital", "sims", "aster", "narayana health",
        "medanta", "columbia asia", "rainbow hospital", "cloudnine", "cloud nine",
        "pharmacy", "medplus", "med plus", "apollo pharmacy", "netmeds",
        "1mg", "tata 1mg", "pharmeasy", "pharm easy", "frank ross", "wellness forever",
        "practo", "doctor", "clinic", "dental", "truemeds", "true meds", "medibuddy",
        "lab", "diagnostic", "thyrocare", "dr lal path", "lal pathlabs", "healthians",
        "srl diagnostics", "metropolis", "vijaya diagnostic", "agilus", "redcliffe labs",
        "optical", "apollo 24", "apollo24", "wellness",
        "gym", "cult.fit", "cultfit", "cure fit", "curefit", "fitness", "anytime fitness",
        "gold's gym", "golds gym", "snap fitness", "healthifyme", "healthify",
    )

    // Education
    private val educationKeywords = listOf(
        "school", "college", "university", "institute",
        "tuition", "coaching", "academy", "vidyalaya", "vidhyalaya",
        "udemy", "coursera", "unacademy", "byju", "byjus", "upgrad", "up grad",
        "skillshare", "linkedin learning", "vedantu", "toppr", "whitehat",
        "physics wallah", "physicswallah", "aakash", "allen career",
        "great learning", "simplilearn", "scaler", "newton school", "codingninjas",
        "coding ninjas", "geeksforgeeks", "cuemath", "extramarks", "embibe",
        "duolingo", "books", "stationery", "sapna book", "crossword",
        "landmark", "higginbothams", "navneet", "classplus", "class plus",
    )

    // Savings & Investments
    private val savingsKeywords = listOf(
        "groww", "zerodha", "kuvera", "coin by zerodha", "indmoney", "ind money",
        "smallcase", "etmoney", "et money", "paytm money", "angel one", "angelone",
        "dhan app", "fyers", "sharekhan", "iifl", "iifl securities", "jar app", "jarapp",
        "wint wealth", "gripinvest", "grip invest", "bharat bond", "navi mutual",
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

    // Subscriptions — recurring digital services & memberships not covered elsewhere
    private val subscriptionKeywords = listOf(
        "google one", "google storage", "icloud", "apple.com/bill", "apple services",
        "microsoft 365", "office 365", "adobe", "canva", "notion", "dropbox",
        "chatgpt", "openai", "github", "linkedin premium", "grammarly",
        "amazon prime membership", "flipkart plus", "zomato gold", "swiggy one",
        "zepto pass", "cult pass", "audible", "kindle unlimited", "scribd",
    )

    // Fuel
    private val fuelKeywords = listOf(
        "petrol", "diesel", "fuel station",
        "hp pump", "hp petrol", "hindustan petroleum",
        "iocl", "indian oil", "bharat petroleum", "bpcl",
        "shell", "nayara", "essar", "reliance petrol", "jio-bp", "jio bp",
        "ev charging", "ather", "charging station", "statiq", "tata power ev",
        "chargezone", "charge zone", "ather grid", "petrol pump", "filling station",
        "hpcl", "ioc ", "reliance bp",
    )

    // Travel
    private val travelKeywords = listOf(
        "makemytrip", "goibibo", "cleartrip", "yatra",
        "easemytrip", "ixigo", "via.com",
        "oyo", "treebo", "fabhotel", "fab hotel", "airbnb",
        "booking.com", "agoda", "trivago", "hostelworld", "zostel",
        "indigo", "spicejet", "spice jet", "air india", "vistara",
        "akasa", "akasa air", "flight", "airline", "goair", "go first",
        "taj hotel", "itc hotel", "marriott", "hyatt", "radisson", "lemon tree",
        "the leela", "leela palace", "oberoi", "novotel", "ibis", "sarovar",
        "ginger hotel", "sterling holiday", "club mahindra", "mahindra holidays",
    )

    // The main database: keywords → category
    // Order matters — first match wins. Travel before food so specific hotel brands
    // (Taj Hotel, Marriott) match before generic "hotel" in food keywords.
    private val merchantDatabase = listOf(
        subscriptionKeywords to TransactionCategory.SUBSCRIPTION,
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
