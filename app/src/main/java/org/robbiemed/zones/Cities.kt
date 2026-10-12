package org.robbiemed.zones

import java.util.TimeZone

data class City(val name: String, val country: String, val tz: String, val lat: Double, val lon: Double) {
    fun encode() = "$name|$country|$tz|$lat|$lon"
    val zone: TimeZone get() = TimeZone.getTimeZone(tz)

    companion object {
        fun decode(s: String) = runCatching {
            val p = s.split('|'); City(p[0], p[1], p[2], p[3].toDouble(), p[4].toDouble())
        }.getOrNull()

        /** Any zone id, even without coordinates: longitude guessed from the UTC offset. */
        fun fromZone(id: String): City {
            known.firstOrNull { it.tz == id }?.let { return it }
            val parts = id.split('/')
            val off = TimeZone.getTimeZone(id).rawOffset / 3_600_000.0
            return City(parts.last().replace('_', ' '), parts.first(), id, 30.0, off * 15)
        }

        val known: List<City> by lazy {
            DB.trimIndent().lines().mapNotNull { l ->
                val p = l.split('|'); if (p.size < 5) null else City(p[0], p[1], p[2], p[3].toDouble(), p[4].toDouble())
            }
        }

        private const val DB = """
            Honolulu|USA|Pacific/Honolulu|21.3|-157.9
            Anchorage|USA|America/Anchorage|61.2|-149.9
            Los Angeles|USA|America/Los_Angeles|34.1|-118.2
            San Francisco|USA|America/Los_Angeles|37.8|-122.4
            Seattle|USA|America/Los_Angeles|47.6|-122.3
            Las Vegas|USA|America/Los_Angeles|36.2|-115.1
            Vancouver|Canada|America/Vancouver|49.3|-123.1
            Phoenix|USA|America/Phoenix|33.4|-112.1
            Denver|USA|America/Denver|39.7|-105.0
            Salt Lake City|USA|America/Denver|40.8|-111.9
            Calgary|Canada|America/Edmonton|51.0|-114.1
            Chicago|USA|America/Chicago|41.9|-87.6
            Oklahoma City|USA|America/Chicago|35.5|-97.5
            Dallas|USA|America/Chicago|32.8|-96.8
            Houston|USA|America/Chicago|29.8|-95.4
            Austin|USA|America/Chicago|30.3|-97.7
            Kansas City|USA|America/Chicago|39.1|-94.6
            Minneapolis|USA|America/Chicago|45.0|-93.3
            New Orleans|USA|America/Chicago|30.0|-90.1
            Mexico City|Mexico|America/Mexico_City|19.4|-99.1
            Guatemala City|Guatemala|America/Guatemala|14.6|-90.5
            New York|USA|America/New_York|40.7|-74.0
            Boston|USA|America/New_York|42.4|-71.1
            Washington DC|USA|America/New_York|38.9|-77.0
            Atlanta|USA|America/New_York|33.7|-84.4
            Miami|USA|America/New_York|25.8|-80.2
            Detroit|USA|America/Detroit|42.3|-83.0
            Toronto|Canada|America/Toronto|43.7|-79.4
            Montreal|Canada|America/Toronto|45.5|-73.6
            Havana|Cuba|America/Havana|23.1|-82.4
            Bogotá|Colombia|America/Bogota|4.7|-74.1
            Lima|Peru|America/Lima|-12.0|-77.0
            Panama City|Panama|America/Panama|9.0|-79.5
            Caracas|Venezuela|America/Caracas|10.5|-66.9
            Halifax|Canada|America/Halifax|44.6|-63.6
            Santiago|Chile|America/Santiago|-33.4|-70.7
            San Juan|Puerto Rico|America/Puerto_Rico|18.5|-66.1
            St. John's|Canada|America/St_Johns|47.6|-52.7
            Buenos Aires|Argentina|America/Argentina/Buenos_Aires|-34.6|-58.4
            São Paulo|Brazil|America/Sao_Paulo|-23.6|-46.6
            Rio de Janeiro|Brazil|America/Sao_Paulo|-22.9|-43.2
            Montevideo|Uruguay|America/Montevideo|-34.9|-56.2
            Nuuk|Greenland|America/Nuuk|64.2|-51.7
            Azores|Portugal|Atlantic/Azores|37.7|-25.7
            Reykjavík|Iceland|Atlantic/Reykjavik|64.1|-21.9
            London|UK|Europe/London|51.5|-0.1
            Dublin|Ireland|Europe/Dublin|53.3|-6.3
            Lisbon|Portugal|Europe/Lisbon|38.7|-9.1
            Casablanca|Morocco|Africa/Casablanca|33.6|-7.6
            Accra|Ghana|Africa/Accra|5.6|-0.2
            Dakar|Senegal|Africa/Dakar|14.7|-17.4
            Paris|France|Europe/Paris|48.9|2.4
            Madrid|Spain|Europe/Madrid|40.4|-3.7
            Barcelona|Spain|Europe/Madrid|41.4|2.2
            Berlin|Germany|Europe/Berlin|52.5|13.4
            Munich|Germany|Europe/Berlin|48.1|11.6
            Amsterdam|Netherlands|Europe/Amsterdam|52.4|4.9
            Brussels|Belgium|Europe/Brussels|50.8|4.4
            Zurich|Switzerland|Europe/Zurich|47.4|8.5
            Geneva|Switzerland|Europe/Zurich|46.2|6.1
            Rome|Italy|Europe/Rome|41.9|12.5
            Milan|Italy|Europe/Rome|45.5|9.2
            Vienna|Austria|Europe/Vienna|48.2|16.4
            Prague|Czechia|Europe/Prague|50.1|14.4
            Warsaw|Poland|Europe/Warsaw|52.2|21.0
            Budapest|Hungary|Europe/Budapest|47.5|19.0
            Copenhagen|Denmark|Europe/Copenhagen|55.7|12.6
            Stockholm|Sweden|Europe/Stockholm|59.3|18.1
            Oslo|Norway|Europe/Oslo|59.9|10.8
            Belgrade|Serbia|Europe/Belgrade|44.8|20.5
            Lagos|Nigeria|Africa/Lagos|6.5|3.4
            Kinshasa|DR Congo|Africa/Kinshasa|-4.3|15.3
            Helsinki|Finland|Europe/Helsinki|60.2|24.9
            Athens|Greece|Europe/Athens|38.0|23.7
            Bucharest|Romania|Europe/Bucharest|44.4|26.1
            Kyiv|Ukraine|Europe/Kyiv|50.5|30.5
            Riga|Latvia|Europe/Riga|56.9|24.1
            Cairo|Egypt|Africa/Cairo|30.0|31.2
            Jerusalem|Israel|Asia/Jerusalem|31.8|35.2
            Tel Aviv|Israel|Asia/Jerusalem|32.1|34.8
            Beirut|Lebanon|Asia/Beirut|33.9|35.5
            Amman|Jordan|Asia/Amman|31.9|35.9
            Johannesburg|South Africa|Africa/Johannesburg|-26.2|28.0
            Cape Town|South Africa|Africa/Johannesburg|-33.9|18.4
            Harare|Zimbabwe|Africa/Harare|-17.8|31.0
            Istanbul|Türkiye|Europe/Istanbul|41.0|29.0
            Moscow|Russia|Europe/Moscow|55.8|37.6
            Riyadh|Saudi Arabia|Asia/Riyadh|24.7|46.7
            Mecca|Saudi Arabia|Asia/Riyadh|21.4|39.8
            Doha|Qatar|Asia/Qatar|25.3|51.5
            Baghdad|Iraq|Asia/Baghdad|33.3|44.4
            Nairobi|Kenya|Africa/Nairobi|-1.3|36.8
            Addis Ababa|Ethiopia|Africa/Addis_Ababa|9.0|38.7
            Tehran|Iran|Asia/Tehran|35.7|51.4
            Dubai|UAE|Asia/Dubai|25.2|55.3
            Abu Dhabi|UAE|Asia/Dubai|24.5|54.4
            Baku|Azerbaijan|Asia/Baku|40.4|49.9
            Tbilisi|Georgia|Asia/Tbilisi|41.7|44.8
            Kabul|Afghanistan|Asia/Kabul|34.5|69.2
            Karachi|Pakistan|Asia/Karachi|24.9|67.0
            Lahore|Pakistan|Asia/Karachi|31.5|74.3
            Tashkent|Uzbekistan|Asia/Tashkent|41.3|69.3
            Delhi|India|Asia/Kolkata|28.6|77.2
            Mumbai|India|Asia/Kolkata|19.1|72.9
            Bangalore|India|Asia/Kolkata|13.0|77.6
            Kolkata|India|Asia/Kolkata|22.6|88.4
            Colombo|Sri Lanka|Asia/Colombo|6.9|79.9
            Kathmandu|Nepal|Asia/Kathmandu|27.7|85.3
            Dhaka|Bangladesh|Asia/Dhaka|23.8|90.4
            Almaty|Kazakhstan|Asia/Almaty|43.2|76.9
            Yangon|Myanmar|Asia/Yangon|16.8|96.2
            Bangkok|Thailand|Asia/Bangkok|13.8|100.5
            Hanoi|Vietnam|Asia/Bangkok|21.0|105.8
            Ho Chi Minh City|Vietnam|Asia/Ho_Chi_Minh|10.8|106.7
            Jakarta|Indonesia|Asia/Jakarta|-6.2|106.8
            Bali|Indonesia|Asia/Makassar|-8.7|115.2
            Singapore|Singapore|Asia/Singapore|1.35|103.8
            Kuala Lumpur|Malaysia|Asia/Kuala_Lumpur|3.1|101.7
            Manila|Philippines|Asia/Manila|14.6|121.0
            Hong Kong|China|Asia/Hong_Kong|22.3|114.2
            Shanghai|China|Asia/Shanghai|31.2|121.5
            Beijing|China|Asia/Shanghai|39.9|116.4
            Shenzhen|China|Asia/Shanghai|22.5|114.1
            Taipei|Taiwan|Asia/Taipei|25.0|121.6
            Perth|Australia|Australia/Perth|-31.9|115.9
            Ulaanbaatar|Mongolia|Asia/Ulaanbaatar|47.9|106.9
            Seoul|South Korea|Asia/Seoul|37.6|127.0
            Busan|South Korea|Asia/Seoul|35.2|129.1
            Incheon|South Korea|Asia/Seoul|37.5|126.7
            Daegu|South Korea|Asia/Seoul|35.9|128.6
            Jeju|South Korea|Asia/Seoul|33.5|126.5
            Pyongyang|North Korea|Asia/Pyongyang|39.0|125.8
            Tokyo|Japan|Asia/Tokyo|35.7|139.7
            Osaka|Japan|Asia/Tokyo|34.7|135.5
            Sapporo|Japan|Asia/Tokyo|43.1|141.4
            Okinawa|Japan|Asia/Tokyo|26.2|127.7
            Guam|USA|Pacific/Guam|13.4|144.8
            Adelaide|Australia|Australia/Adelaide|-34.9|138.6
            Darwin|Australia|Australia/Darwin|-12.5|130.8
            Brisbane|Australia|Australia/Brisbane|-27.5|153.0
            Sydney|Australia|Australia/Sydney|-33.9|151.2
            Melbourne|Australia|Australia/Melbourne|-37.8|145.0
            Vladivostok|Russia|Asia/Vladivostok|43.1|131.9
            Nouméa|New Caledonia|Pacific/Noumea|-22.3|166.5
            Auckland|New Zealand|Pacific/Auckland|-36.8|174.8
            Wellington|New Zealand|Pacific/Auckland|-41.3|174.8
            Fiji|Fiji|Pacific/Fiji|-18.1|178.4
            Samoa|Samoa|Pacific/Apia|-13.8|-171.8
            Kiritimati|Kiribati|Pacific/Kiritimati|1.9|-157.5
            UTC|Coordinated Universal Time|UTC|51.5|0.0
        """
    }
}
