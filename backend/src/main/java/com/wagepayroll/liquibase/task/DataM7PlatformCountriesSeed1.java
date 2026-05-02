package com.wagepayroll.liquibase.task;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

/**
 * Seeds the global ISO country catalog with EN/NL translations.
 */
public class DataM7PlatformCountriesSeed1 implements CustomTaskChange {

	private static final List<CountrySeed> COUNTRIES = List.of(
		new CountrySeed("AD", "AND", "020", "+376", "Andorra", "Andorra"),
		new CountrySeed("AE", "ARE", "784", "+971", "United Arab Emirates", "Verenigde Arabische Emiraten"),
		new CountrySeed("AF", "AFG", "004", "+93", "Afghanistan", "Afghanistan"),
		new CountrySeed("AG", "ATG", "028", "+1268", "Antigua and Barbuda", "Antigua en Barbuda"),
		new CountrySeed("AI", "AIA", "660", "+1264", "Anguilla", "Anguilla"),
		new CountrySeed("AL", "ALB", "008", "+355", "Albania", "Albanië"),
		new CountrySeed("AM", "ARM", "051", "+374", "Armenia", "Armenië"),
		new CountrySeed("AO", "AGO", "024", "+244", "Angola", "Angola"),
		new CountrySeed("AQ", "ATA", "010", null, "Antarctica", "Antarctica"),
		new CountrySeed("AR", "ARG", "032", "+54", "Argentina", "Argentinië"),
		new CountrySeed("AS", "ASM", "016", "+1684", "American Samoa", "Amerikaans-Samoa"),
		new CountrySeed("AT", "AUT", "040", "+43", "Austria", "Oostenrijk"),
		new CountrySeed("AU", "AUS", "036", "+61", "Australia", "Australië"),
		new CountrySeed("AW", "ABW", "533", "+297", "Aruba", "Aruba"),
		new CountrySeed("AX", "ALA", "248", "+35818", "Åland Islands", "Åland"),
		new CountrySeed("AZ", "AZE", "031", "+994", "Azerbaijan", "Azerbeidzjan"),
		new CountrySeed("BA", "BIH", "070", "+387", "Bosnia and Herzegovina", "Bosnië en Herzegovina"),
		new CountrySeed("BB", "BRB", "052", "+1246", "Barbados", "Barbados"),
		new CountrySeed("BD", "BGD", "050", "+880", "Bangladesh", "Bangladesh"),
		new CountrySeed("BE", "BEL", "056", "+32", "Belgium", "België"),
		new CountrySeed("BF", "BFA", "854", "+226", "Burkina Faso", "Burkina Faso"),
		new CountrySeed("BG", "BGR", "100", "+359", "Bulgaria", "Bulgarije"),
		new CountrySeed("BH", "BHR", "048", "+973", "Bahrain", "Bahrein"),
		new CountrySeed("BI", "BDI", "108", "+257", "Burundi", "Burundi"),
		new CountrySeed("BJ", "BEN", "204", "+229", "Benin", "Benin"),
		new CountrySeed("BL", "BLM", "652", "+590", "Saint Barthélemy", "Saint-Barthélemy"),
		new CountrySeed("BM", "BMU", "060", "+1441", "Bermuda", "Bermuda"),
		new CountrySeed("BN", "BRN", "096", "+673", "Brunei", "Brunei"),
		new CountrySeed("BO", "BOL", "068", "+591", "Bolivia", "Bolivia"),
		new CountrySeed("BQ", "BES", "535", "+599", "Caribbean Netherlands", "Caribisch Nederland"),
		new CountrySeed("BR", "BRA", "076", "+55", "Brazil", "Brazilië"),
		new CountrySeed("BS", "BHS", "044", "+1242", "Bahamas", "Bahama’s"),
		new CountrySeed("BT", "BTN", "064", "+975", "Bhutan", "Bhutan"),
		new CountrySeed("BV", "BVT", "074", "+47", "Bouvet Island", "Bouveteiland"),
		new CountrySeed("BW", "BWA", "072", "+267", "Botswana", "Botswana"),
		new CountrySeed("BY", "BLR", "112", "+375", "Belarus", "Belarus"),
		new CountrySeed("BZ", "BLZ", "084", "+501", "Belize", "Belize"),
		new CountrySeed("CA", "CAN", "124", null, "Canada", "Canada"),
		new CountrySeed("CC", "CCK", "166", "+61", "Cocos (Keeling) Islands", "Cocoseilanden"),
		new CountrySeed("CD", "COD", "180", "+243", "DR Congo", "Congo-Kinshasa"),
		new CountrySeed("CF", "CAF", "140", "+236", "Central African Republic", "Centraal-Afrikaanse Republiek"),
		new CountrySeed("CG", "COG", "178", "+242", "Republic of the Congo", "Congo-Brazzaville"),
		new CountrySeed("CH", "CHE", "756", "+41", "Switzerland", "Zwitserland"),
		new CountrySeed("CI", "CIV", "384", "+225", "Ivory Coast", "Ivoorkust"),
		new CountrySeed("CK", "COK", "184", "+682", "Cook Islands", "Cookeilanden"),
		new CountrySeed("CL", "CHL", "152", "+56", "Chile", "Chili"),
		new CountrySeed("CM", "CMR", "120", "+237", "Cameroon", "Kameroen"),
		new CountrySeed("CN", "CHN", "156", "+86", "China", "China"),
		new CountrySeed("CO", "COL", "170", "+57", "Colombia", "Colombia"),
		new CountrySeed("CR", "CRI", "188", "+506", "Costa Rica", "Costa Rica"),
		new CountrySeed("CU", "CUB", "192", "+53", "Cuba", "Cuba"),
		new CountrySeed("CV", "CPV", "132", "+238", "Cape Verde", "Kaapverdië"),
		new CountrySeed("CW", "CUW", "531", "+599", "Curaçao", "Curaçao"),
		new CountrySeed("CX", "CXR", "162", "+61", "Christmas Island", "Christmaseiland"),
		new CountrySeed("CY", "CYP", "196", "+357", "Cyprus", "Cyprus"),
		new CountrySeed("CZ", "CZE", "203", "+420", "Czechia", "Tsjechië"),
		new CountrySeed("DE", "DEU", "276", "+49", "Germany", "Duitsland"),
		new CountrySeed("DJ", "DJI", "262", "+253", "Djibouti", "Djibouti"),
		new CountrySeed("DK", "DNK", "208", "+45", "Denmark", "Denemarken"),
		new CountrySeed("DM", "DMA", "212", "+1767", "Dominica", "Dominica"),
		new CountrySeed("DO", "DOM", "214", "+1809", "Dominican Republic", "Dominicaanse Republiek"),
		new CountrySeed("DZ", "DZA", "012", "+213", "Algeria", "Algerije"),
		new CountrySeed("EC", "ECU", "218", "+593", "Ecuador", "Ecuador"),
		new CountrySeed("EE", "EST", "233", "+372", "Estonia", "Estland"),
		new CountrySeed("EG", "EGY", "818", "+20", "Egypt", "Egypte"),
		new CountrySeed("EH", "ESH", "732", "+2125288", "Western Sahara", "Westelijke Sahara"),
		new CountrySeed("ER", "ERI", "232", "+291", "Eritrea", "Eritrea"),
		new CountrySeed("ES", "ESP", "724", "+34", "Spain", "Spanje"),
		new CountrySeed("ET", "ETH", "231", "+251", "Ethiopia", "Ethiopië"),
		new CountrySeed("FI", "FIN", "246", "+358", "Finland", "Finland"),
		new CountrySeed("FJ", "FJI", "242", "+679", "Fiji", "Fiji"),
		new CountrySeed("FK", "FLK", "238", "+500", "Falkland Islands", "Falklandeilanden"),
		new CountrySeed("FM", "FSM", "583", "+691", "Micronesia", "Micronesia"),
		new CountrySeed("FO", "FRO", "234", "+298", "Faroe Islands", "Faeröer"),
		new CountrySeed("FR", "FRA", "250", "+33", "France", "Frankrijk"),
		new CountrySeed("GA", "GAB", "266", "+241", "Gabon", "Gabon"),
		new CountrySeed("GB", "GBR", "826", "+44", "United Kingdom", "Verenigd Koninkrijk"),
		new CountrySeed("GD", "GRD", "308", "+1473", "Grenada", "Grenada"),
		new CountrySeed("GE", "GEO", "268", "+995", "Georgia", "Georgië"),
		new CountrySeed("GF", "GUF", "254", "+594", "French Guiana", "Frans-Guyana"),
		new CountrySeed("GG", "GGY", "831", "+44", "Guernsey", "Guernsey"),
		new CountrySeed("GH", "GHA", "288", "+233", "Ghana", "Ghana"),
		new CountrySeed("GI", "GIB", "292", "+350", "Gibraltar", "Gibraltar"),
		new CountrySeed("GL", "GRL", "304", "+299", "Greenland", "Groenland"),
		new CountrySeed("GM", "GMB", "270", "+220", "Gambia", "Gambia"),
		new CountrySeed("GN", "GIN", "324", "+224", "Guinea", "Guinee"),
		new CountrySeed("GP", "GLP", "312", "+590", "Guadeloupe", "Guadeloupe"),
		new CountrySeed("GQ", "GNQ", "226", "+240", "Equatorial Guinea", "Equatoriaal-Guinea"),
		new CountrySeed("GR", "GRC", "300", "+30", "Greece", "Griekenland"),
		new CountrySeed("GS", "SGS", "239", "+500", "South Georgia", "Zuid-Georgia en Zuidelijke Sandwicheilanden"),
		new CountrySeed("GT", "GTM", "320", "+502", "Guatemala", "Guatemala"),
		new CountrySeed("GU", "GUM", "316", "+1671", "Guam", "Guam"),
		new CountrySeed("GW", "GNB", "624", "+245", "Guinea-Bissau", "Guinee-Bissau"),
		new CountrySeed("GY", "GUY", "328", "+592", "Guyana", "Guyana"),
		new CountrySeed("HK", "HKG", "344", "+852", "Hong Kong", "Hongkong SAR van China"),
		new CountrySeed("HM", "HMD", "334", null, "Heard Island and McDonald Islands", "Heard en McDonaldeilanden"),
		new CountrySeed("HN", "HND", "340", "+504", "Honduras", "Honduras"),
		new CountrySeed("HR", "HRV", "191", "+385", "Croatia", "Kroatië"),
		new CountrySeed("HT", "HTI", "332", "+509", "Haiti", "Haïti"),
		new CountrySeed("HU", "HUN", "348", "+36", "Hungary", "Hongarije"),
		new CountrySeed("ID", "IDN", "360", "+62", "Indonesia", "Indonesië"),
		new CountrySeed("IE", "IRL", "372", "+353", "Ireland", "Ierland"),
		new CountrySeed("IL", "ISR", "376", "+972", "Israel", "Israël"),
		new CountrySeed("IM", "IMN", "833", "+44", "Isle of Man", "Isle of Man"),
		new CountrySeed("IN", "IND", "356", "+91", "India", "India"),
		new CountrySeed("IO", "IOT", "086", "+246", "British Indian Ocean Territory", "Brits Indische Oceaanterritorium"),
		new CountrySeed("IQ", "IRQ", "368", "+964", "Iraq", "Irak"),
		new CountrySeed("IR", "IRN", "364", "+98", "Iran", "Iran"),
		new CountrySeed("IS", "ISL", "352", "+354", "Iceland", "IJsland"),
		new CountrySeed("IT", "ITA", "380", "+39", "Italy", "Italië"),
		new CountrySeed("JE", "JEY", "832", "+44", "Jersey", "Jersey"),
		new CountrySeed("JM", "JAM", "388", "+1876", "Jamaica", "Jamaica"),
		new CountrySeed("JO", "JOR", "400", "+962", "Jordan", "Jordanië"),
		new CountrySeed("JP", "JPN", "392", "+81", "Japan", "Japan"),
		new CountrySeed("KE", "KEN", "404", "+254", "Kenya", "Kenia"),
		new CountrySeed("KG", "KGZ", "417", "+996", "Kyrgyzstan", "Kirgizië"),
		new CountrySeed("KH", "KHM", "116", "+855", "Cambodia", "Cambodja"),
		new CountrySeed("KI", "KIR", "296", "+686", "Kiribati", "Kiribati"),
		new CountrySeed("KM", "COM", "174", "+269", "Comoros", "Comoren"),
		new CountrySeed("KN", "KNA", "659", "+1869", "Saint Kitts and Nevis", "Saint Kitts en Nevis"),
		new CountrySeed("KP", "PRK", "408", "+850", "North Korea", "Noord-Korea"),
		new CountrySeed("KR", "KOR", "410", "+82", "South Korea", "Zuid-Korea"),
		new CountrySeed("KW", "KWT", "414", "+965", "Kuwait", "Koeweit"),
		new CountrySeed("KY", "CYM", "136", "+1345", "Cayman Islands", "Kaaimaneilanden"),
		new CountrySeed("KZ", "KAZ", "398", "+76", "Kazakhstan", "Kazachstan"),
		new CountrySeed("LA", "LAO", "418", "+856", "Laos", "Laos"),
		new CountrySeed("LB", "LBN", "422", "+961", "Lebanon", "Libanon"),
		new CountrySeed("LC", "LCA", "662", "+1758", "Saint Lucia", "Saint Lucia"),
		new CountrySeed("LI", "LIE", "438", "+423", "Liechtenstein", "Liechtenstein"),
		new CountrySeed("LK", "LKA", "144", "+94", "Sri Lanka", "Sri Lanka"),
		new CountrySeed("LR", "LBR", "430", "+231", "Liberia", "Liberia"),
		new CountrySeed("LS", "LSO", "426", "+266", "Lesotho", "Lesotho"),
		new CountrySeed("LT", "LTU", "440", "+370", "Lithuania", "Litouwen"),
		new CountrySeed("LU", "LUX", "442", "+352", "Luxembourg", "Luxemburg"),
		new CountrySeed("LV", "LVA", "428", "+371", "Latvia", "Letland"),
		new CountrySeed("LY", "LBY", "434", "+218", "Libya", "Libië"),
		new CountrySeed("MA", "MAR", "504", "+212", "Morocco", "Marokko"),
		new CountrySeed("MC", "MCO", "492", "+377", "Monaco", "Monaco"),
		new CountrySeed("MD", "MDA", "498", "+373", "Moldova", "Moldavië"),
		new CountrySeed("ME", "MNE", "499", "+382", "Montenegro", "Montenegro"),
		new CountrySeed("MF", "MAF", "663", "+590", "Saint Martin", "Saint-Martin"),
		new CountrySeed("MG", "MDG", "450", "+261", "Madagascar", "Madagaskar"),
		new CountrySeed("MH", "MHL", "584", "+692", "Marshall Islands", "Marshalleilanden"),
		new CountrySeed("MK", "MKD", "807", "+389", "North Macedonia", "Noord-Macedonië"),
		new CountrySeed("ML", "MLI", "466", "+223", "Mali", "Mali"),
		new CountrySeed("MM", "MMR", "104", "+95", "Myanmar", "Myanmar (Birma)"),
		new CountrySeed("MN", "MNG", "496", "+976", "Mongolia", "Mongolië"),
		new CountrySeed("MO", "MAC", "446", "+853", "Macau", "Macau SAR van China"),
		new CountrySeed("MP", "MNP", "580", "+1670", "Northern Mariana Islands", "Noordelijke Marianen"),
		new CountrySeed("MQ", "MTQ", "474", "+596", "Martinique", "Martinique"),
		new CountrySeed("MR", "MRT", "478", "+222", "Mauritania", "Mauritanië"),
		new CountrySeed("MS", "MSR", "500", "+1664", "Montserrat", "Montserrat"),
		new CountrySeed("MT", "MLT", "470", "+356", "Malta", "Malta"),
		new CountrySeed("MU", "MUS", "480", "+230", "Mauritius", "Mauritius"),
		new CountrySeed("MV", "MDV", "462", "+960", "Maldives", "Maldiven"),
		new CountrySeed("MW", "MWI", "454", "+265", "Malawi", "Malawi"),
		new CountrySeed("MX", "MEX", "484", "+52", "Mexico", "Mexico"),
		new CountrySeed("MY", "MYS", "458", "+60", "Malaysia", "Maleisië"),
		new CountrySeed("MZ", "MOZ", "508", "+258", "Mozambique", "Mozambique"),
		new CountrySeed("NA", "NAM", "516", "+264", "Namibia", "Namibië"),
		new CountrySeed("NC", "NCL", "540", "+687", "New Caledonia", "Nieuw-Caledonië"),
		new CountrySeed("NE", "NER", "562", "+227", "Niger", "Niger"),
		new CountrySeed("NF", "NFK", "574", "+672", "Norfolk Island", "Norfolk"),
		new CountrySeed("NG", "NGA", "566", "+234", "Nigeria", "Nigeria"),
		new CountrySeed("NI", "NIC", "558", "+505", "Nicaragua", "Nicaragua"),
		new CountrySeed("NL", "NLD", "528", "+31", "Netherlands", "Nederland"),
		new CountrySeed("NO", "NOR", "578", "+47", "Norway", "Noorwegen"),
		new CountrySeed("NP", "NPL", "524", "+977", "Nepal", "Nepal"),
		new CountrySeed("NR", "NRU", "520", "+674", "Nauru", "Nauru"),
		new CountrySeed("NU", "NIU", "570", "+683", "Niue", "Niue"),
		new CountrySeed("NZ", "NZL", "554", "+64", "New Zealand", "Nieuw-Zeeland"),
		new CountrySeed("OM", "OMN", "512", "+968", "Oman", "Oman"),
		new CountrySeed("PA", "PAN", "591", "+507", "Panama", "Panama"),
		new CountrySeed("PE", "PER", "604", "+51", "Peru", "Peru"),
		new CountrySeed("PF", "PYF", "258", "+689", "French Polynesia", "Frans-Polynesië"),
		new CountrySeed("PG", "PNG", "598", "+675", "Papua New Guinea", "Papoea-Nieuw-Guinea"),
		new CountrySeed("PH", "PHL", "608", "+63", "Philippines", "Filipijnen"),
		new CountrySeed("PK", "PAK", "586", "+92", "Pakistan", "Pakistan"),
		new CountrySeed("PL", "POL", "616", "+48", "Poland", "Polen"),
		new CountrySeed("PM", "SPM", "666", "+508", "Saint Pierre and Miquelon", "Saint-Pierre en Miquelon"),
		new CountrySeed("PN", "PCN", "612", "+64", "Pitcairn Islands", "Pitcairneilanden"),
		new CountrySeed("PR", "PRI", "630", "+1787", "Puerto Rico", "Puerto Rico"),
		new CountrySeed("PS", "PSE", "275", "+970", "Palestine", "Palestijnse gebieden"),
		new CountrySeed("PT", "PRT", "620", "+351", "Portugal", "Portugal"),
		new CountrySeed("PW", "PLW", "585", "+680", "Palau", "Palau"),
		new CountrySeed("PY", "PRY", "600", "+595", "Paraguay", "Paraguay"),
		new CountrySeed("QA", "QAT", "634", "+974", "Qatar", "Qatar"),
		new CountrySeed("RE", "REU", "638", "+262", "Réunion", "Réunion"),
		new CountrySeed("RO", "ROU", "642", "+40", "Romania", "Roemenië"),
		new CountrySeed("RS", "SRB", "688", "+381", "Serbia", "Servië"),
		new CountrySeed("RU", "RUS", "643", "+73", "Russia", "Rusland"),
		new CountrySeed("RW", "RWA", "646", "+250", "Rwanda", "Rwanda"),
		new CountrySeed("SA", "SAU", "682", "+966", "Saudi Arabia", "Saoedi-Arabië"),
		new CountrySeed("SB", "SLB", "090", "+677", "Solomon Islands", "Salomonseilanden"),
		new CountrySeed("SC", "SYC", "690", "+248", "Seychelles", "Seychellen"),
		new CountrySeed("SD", "SDN", "729", "+249", "Sudan", "Soedan"),
		new CountrySeed("SE", "SWE", "752", "+46", "Sweden", "Zweden"),
		new CountrySeed("SG", "SGP", "702", "+65", "Singapore", "Singapore"),
		new CountrySeed("SH", "SHN", "654", "+290", "Saint Helena, Ascension and Tristan da Cunha", "Sint-Helena"),
		new CountrySeed("SI", "SVN", "705", "+386", "Slovenia", "Slovenië"),
		new CountrySeed("SJ", "SJM", "744", "+4779", "Svalbard and Jan Mayen", "Spitsbergen en Jan Mayen"),
		new CountrySeed("SK", "SVK", "703", "+421", "Slovakia", "Slowakije"),
		new CountrySeed("SL", "SLE", "694", "+232", "Sierra Leone", "Sierra Leone"),
		new CountrySeed("SM", "SMR", "674", "+378", "San Marino", "San Marino"),
		new CountrySeed("SN", "SEN", "686", "+221", "Senegal", "Senegal"),
		new CountrySeed("SO", "SOM", "706", "+252", "Somalia", "Somalië"),
		new CountrySeed("SR", "SUR", "740", "+597", "Suriname", "Suriname"),
		new CountrySeed("SS", "SSD", "728", "+211", "South Sudan", "Zuid-Soedan"),
		new CountrySeed("ST", "STP", "678", "+239", "São Tomé and Príncipe", "Sao Tomé en Principe"),
		new CountrySeed("SV", "SLV", "222", "+503", "El Salvador", "El Salvador"),
		new CountrySeed("SX", "SXM", "534", "+1721", "Sint Maarten", "Sint-Maarten"),
		new CountrySeed("SY", "SYR", "760", "+963", "Syria", "Syrië"),
		new CountrySeed("SZ", "SWZ", "748", "+268", "Eswatini", "Eswatini"),
		new CountrySeed("TC", "TCA", "796", "+1649", "Turks and Caicos Islands", "Turks- en Caicoseilanden"),
		new CountrySeed("TD", "TCD", "148", "+235", "Chad", "Tsjaad"),
		new CountrySeed("TF", "ATF", "260", "+262", "French Southern and Antarctic Lands", "Franse Gebieden in de zuidelijke Indische Oceaan"),
		new CountrySeed("TG", "TGO", "768", "+228", "Togo", "Togo"),
		new CountrySeed("TH", "THA", "764", "+66", "Thailand", "Thailand"),
		new CountrySeed("TJ", "TJK", "762", "+992", "Tajikistan", "Tadzjikistan"),
		new CountrySeed("TK", "TKL", "772", "+690", "Tokelau", "Tokelau"),
		new CountrySeed("TL", "TLS", "626", "+670", "Timor-Leste", "Oost-Timor"),
		new CountrySeed("TM", "TKM", "795", "+993", "Turkmenistan", "Turkmenistan"),
		new CountrySeed("TN", "TUN", "788", "+216", "Tunisia", "Tunesië"),
		new CountrySeed("TO", "TON", "776", "+676", "Tonga", "Tonga"),
		new CountrySeed("TR", "TUR", "792", "+90", "Turkey", "Turkije"),
		new CountrySeed("TT", "TTO", "780", "+1868", "Trinidad and Tobago", "Trinidad en Tobago"),
		new CountrySeed("TV", "TUV", "798", "+688", "Tuvalu", "Tuvalu"),
		new CountrySeed("TW", "TWN", "158", "+886", "Taiwan", "Taiwan"),
		new CountrySeed("TZ", "TZA", "834", "+255", "Tanzania", "Tanzania"),
		new CountrySeed("UA", "UKR", "804", "+380", "Ukraine", "Oekraïne"),
		new CountrySeed("UG", "UGA", "800", "+256", "Uganda", "Oeganda"),
		new CountrySeed("UM", "UMI", "581", "+268", "United States Minor Outlying Islands", "Kleine afgelegen eilanden van de Verenigde Staten"),
		new CountrySeed("US", "USA", "840", "+1201", "United States", "Verenigde Staten"),
		new CountrySeed("UY", "URY", "858", "+598", "Uruguay", "Uruguay"),
		new CountrySeed("UZ", "UZB", "860", "+998", "Uzbekistan", "Oezbekistan"),
		new CountrySeed("VA", "VAT", "336", "+3906698", "Vatican City", "Vaticaanstad"),
		new CountrySeed("VC", "VCT", "670", "+1784", "Saint Vincent and the Grenadines", "Saint Vincent en de Grenadines"),
		new CountrySeed("VE", "VEN", "862", "+58", "Venezuela", "Venezuela"),
		new CountrySeed("VG", "VGB", "092", "+1284", "British Virgin Islands", "Britse Maagdeneilanden"),
		new CountrySeed("VI", "VIR", "850", "+1340", "United States Virgin Islands", "Amerikaanse Maagdeneilanden"),
		new CountrySeed("VN", "VNM", "704", "+84", "Vietnam", "Vietnam"),
		new CountrySeed("VU", "VUT", "548", "+678", "Vanuatu", "Vanuatu"),
		new CountrySeed("WF", "WLF", "876", "+681", "Wallis and Futuna", "Wallis en Futuna"),
		new CountrySeed("WS", "WSM", "882", "+685", "Samoa", "Samoa"),
		new CountrySeed("XK", "UNK", "000", "+383", "Kosovo", "Kosovo"),
		new CountrySeed("YE", "YEM", "887", "+967", "Yemen", "Jemen"),
		new CountrySeed("YT", "MYT", "175", "+262", "Mayotte", "Mayotte"),
		new CountrySeed("ZA", "ZAF", "710", "+27", "South Africa", "Zuid-Afrika"),
		new CountrySeed("ZM", "ZMB", "894", "+260", "Zambia", "Zambia"),
		new CountrySeed("ZW", "ZWE", "716", "+263", "Zimbabwe", "Zimbabwe")
	);

	@Override
	public void execute(Database database) throws CustomChangeException {
		Instant now = Instant.now();
		Timestamp ts = Timestamp.from(now);
		try {
			Connection c = ((JdbcConnection) database.getConnection()).getUnderlyingConnection();
			c.setAutoCommit(false);
			try {
				for (CountrySeed seed : COUNTRIES) {
					upsertCountry(c, seed, ts);
				}
				c.commit();
			}
			catch (Exception e) {
				c.rollback();
				throw e;
			}
		}
		catch (Exception e) {
			throw new CustomChangeException(e.getMessage(), e);
		}
	}

	private static void upsertCountry(Connection c, CountrySeed seed, Timestamp ts) throws Exception {
		UUID countryId = resolveCountryId(c, seed.alpha2());
		if (countryId == null) {
			countryId = deterministic("platform-country:" + seed.alpha2());
			try (PreparedStatement insert = c.prepareStatement(
					"INSERT INTO platform_country (id, iso_alpha2, iso_alpha3, iso_numeric, dial_code, active, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)")) {
				insert.setString(1, countryId.toString());
				insert.setString(2, seed.alpha2());
				insert.setString(3, seed.alpha3());
				insert.setString(4, seed.numeric());
				if (seed.dialCode() == null) {
					insert.setObject(5, null);
				} else {
					insert.setString(5, seed.dialCode());
				}
				insert.setBoolean(6, true);
				insert.setTimestamp(7, ts);
				insert.setTimestamp(8, ts);
				insert.executeUpdate();
			}
		} else {
			try (PreparedStatement update = c.prepareStatement(
					"UPDATE platform_country SET iso_alpha3 = ?, iso_numeric = ?, dial_code = ?, updated_at = ? WHERE id = ?")) {
				update.setString(1, seed.alpha3());
				update.setString(2, seed.numeric());
				if (seed.dialCode() == null) {
					update.setObject(3, null);
				} else {
					update.setString(3, seed.dialCode());
				}
				update.setTimestamp(4, ts);
				update.setString(5, countryId.toString());
				update.executeUpdate();
			}
		}
		upsertTranslation(c, countryId, "en", seed.nameEn());
		upsertTranslation(c, countryId, "nl", seed.nameNl());
	}

	private static UUID resolveCountryId(Connection c, String alpha2) throws Exception {
		try (PreparedStatement ps = c.prepareStatement("SELECT id FROM platform_country WHERE iso_alpha2 = ?")) {
			ps.setString(1, alpha2.toUpperCase(Locale.ROOT));
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) {
					return null;
				}
				return UUID.fromString(rs.getString(1));
			}
		}
	}

	private static void upsertTranslation(Connection c, UUID countryId, String locale, String name) throws Exception {
		try (PreparedStatement check = c.prepareStatement(
				"SELECT id FROM platform_country_translation WHERE country_id = ? AND locale = ?")) {
			check.setString(1, countryId.toString());
			check.setString(2, locale);
			try (ResultSet rs = check.executeQuery()) {
				if (rs.next()) {
					try (PreparedStatement update = c.prepareStatement(
							"UPDATE platform_country_translation SET name = ? WHERE id = ?")) {
						update.setString(1, name);
						update.setString(2, rs.getString(1));
						update.executeUpdate();
					}
					return;
				}
			}
		}

		UUID translationId = deterministic("platform-country-translation:" + countryId + ":" + locale);
		try (PreparedStatement insert = c.prepareStatement(
				"INSERT INTO platform_country_translation (id, country_id, locale, name) VALUES (?,?,?,?)")) {
			insert.setString(1, translationId.toString());
			insert.setString(2, countryId.toString());
			insert.setString(3, locale);
			insert.setString(4, name);
			insert.executeUpdate();
		}
	}

	private static UUID deterministic(String raw) {
		return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public String getConfirmationMessage() {
		return "M7 platform countries seeded";
	}

	@Override
	public void setUp() throws SetupException {
	}

	@Override
	public void setFileOpener(ResourceAccessor resourceAccessor) {
	}

	@Override
	public ValidationErrors validate(Database database) {
		return new ValidationErrors();
	}

	private record CountrySeed(String alpha2, String alpha3, String numeric, String dialCode, String nameEn, String nameNl) {
	}
}
