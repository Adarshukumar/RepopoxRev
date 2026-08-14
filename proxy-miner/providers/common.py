"""Shared helpers/constants for providers."""

# Full country name -> ISO 3166-1 alpha-2 code.
# Used by providers (e.g. NodeMaven) that only give a full country name.
COUNTRY_NAME_TO_CODE = {
    "afghanistan": "AF", "albania": "AL", "algeria": "DZ",
    "argentina": "AR", "armenia": "AM", "australia": "AU",
    "austria": "AT", "azerbaijan": "AZ", "bahrain": "BH",
    "bangladesh": "BD", "belarus": "BY", "belgium": "BE",
    "bolivia": "BO", "bosnia and herzegovina": "BA", "botswana": "BW",
    "brazil": "BR", "bulgaria": "BG", "cambodia": "KH",
    "canada": "CA", "chile": "CL", "china": "CN",
    "colombia": "CO", "costa rica": "CR", "croatia": "HR",
    "cyprus": "CY", "czech republic": "CZ", "czechia": "CZ",
    "denmark": "DK", "dominican republic": "DO", "ecuador": "EC",
    "egypt": "EG", "estonia": "EE", "finland": "FI",
    "france": "FR", "georgia": "GE", "germany": "DE",
    "ghana": "GH", "greece": "GR", "hong kong": "HK",
    "hungary": "HU", "iceland": "IS", "india": "IN",
    "indonesia": "ID", "iran": "IR", "iran, islamic republic of": "IR",
    "iraq": "IQ", "ireland": "IE", "israel": "IL",
    "italy": "IT", "japan": "JP", "jordan": "JO",
    "kazakhstan": "KZ", "kenya": "KE", "korea": "KR",
    "korea, republic of": "KR", "republic of korea": "KR",
    "kyrgyzstan": "KG", "latvia": "LV", "lithuania": "LT",
    "luxembourg": "LU", "malaysia": "MY", "mexico": "MX",
    "moldova": "MD", "mongolia": "MN", "morocco": "MA",
    "nepal": "NP", "netherlands": "NL", "new zealand": "NZ",
    "nigeria": "NG", "norway": "NO", "pakistan": "PK",
    "panama": "PA", "paraguay": "PY", "peru": "PE",
    "philippines": "PH", "poland": "PL", "portugal": "PT",
    "qatar": "QA", "romania": "RO", "russia": "RU",
    "russian federation": "RU", "saudi arabia": "SA", "senegal": "SN",
    "serbia": "RS", "singapore": "SG", "slovakia": "SK",
    "slovenia": "SI", "south africa": "ZA", "spain": "ES",
    "sri lanka": "LK", "sweden": "SE", "switzerland": "CH",
    "taiwan": "TW", "tanzania": "TZ", "thailand": "TH",
    "tunisia": "TN", "turkey": "TR", "ukraine": "UA",
    "united arab emirates": "AE", "united kingdom": "GB", "uk": "GB",
    "united states": "US", "usa": "US", "uruguay": "UY",
    "uzbekistan": "UZ", "venezuela": "VE", "vietnam": "VN",
    "yemen": "YE", "zambia": "ZM", "zimbabwe": "ZW",
}


def country_code(name: str | None) -> str | None:
    """Return ISO2 code for a full country name, or None if unknown."""
    if not name:
        return None
    key = name.strip().lower()
    if len(key) == 2 and key.isalpha():
        return key.upper()
    return COUNTRY_NAME_TO_CODE.get(key)
