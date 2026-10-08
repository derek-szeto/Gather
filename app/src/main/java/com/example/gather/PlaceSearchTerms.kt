package com.example.gather

import java.text.Normalizer
import java.util.Locale

object PlaceSearchTerms {

    data class Match(
        val categories: String,
        val businessName: String,
        val label: String = ""
    )

    private data class Group(
        val label: String,
        val categories: String,
        val aliases: List<String>
    )

    private val groups = listOf(
        Group(
            "Brunch",
            "catering.cafe,catering.restaurant",
            listOf("brunch", "breakfast", "breakfast place")
        ),
        Group(
            "Japanese food",
            "catering.restaurant.japanese",
            listOf("japanese", "japanese restaurant")
        ),
        Group(
            "Korean food",
            "catering.restaurant.korean",
            listOf("korean", "korean restaurant")
        ),
        Group(
            "Thai food",
            "catering.restaurant.thai",
            listOf("thai", "thai restaurant")
        ),
        Group(
            "Vietnamese food",
            "catering.restaurant.vietnamese",
            listOf("vietnamese", "vietnamese restaurant")
        ),
        Group(
            "Greek food",
            "catering.restaurant.greek",
            listOf("greek", "greek restaurant")
        ),
        Group(
            "Seafood",
            "catering.restaurant.seafood",
            listOf("seafood restaurant")
        ),
        Group(
            "Steakhouses",
            "catering.restaurant.steak_house",
            listOf("steakhouse", "steak house")
        ),
        Group(
            "Food courts",
            "catering.food_court",
            listOf("food court")
        ),
        Group(
            "Bakeries",
            "commercial.food_and_drink.bakery",
            listOf("bakery", "bakeshop", "bake shop")
        ),
        Group(
            "Donut shops",
            "catering.cafe.donut",
            listOf("donut", "donuts", "doughnut", "doughnuts")
        ),
        Group(
            "Tea shops",
            "catering.cafe.tea",
            listOf("tea", "tea shop", "tea house")
        ),
        Group(
            "Dessert shops",
            "catering.cafe.dessert",
            listOf("dessert", "desserts", "dessert shop")
        ),
        Group(
            "Bars and pubs",
            "catering.bar,catering.pub,catering.taproom",
            listOf("bar", "bars", "pub", "pubs", "taproom", "cocktail bar")
        ),
        Group(
            "Beer gardens",
            "catering.biergarten",
            listOf("beer garden", "biergarten")
        ),
        Group(
            "Gardens",
            "leisure.park.garden",
            listOf("garden", "botanical garden")
        ),
        Group(
            "Nature reserves",
            "leisure.park.nature_reserve",
            listOf("nature reserve", "nature preserve")
        ),
        Group(
            "Beaches",
            "beach",
            listOf("beach", "beachfront")
        ),
        Group(
            "Scenic viewpoints",
            "tourism.attraction.viewpoint",
            listOf("viewpoint", "scenic overlook", "lookout")
        ),
        Group(
            "Tourist attractions",
            "tourism.attraction",
            listOf("attraction", "sightseeing", "tourist attraction")
        ),
        Group(
            "Historic sights",
            "tourism.sights",
            listOf("historic site", "landmark", "monument")
        ),
        Group(
            "Campgrounds",
            "camping.camp_site",
            listOf("campground", "camping", "camp site", "campsite")
        ),
        Group(
            "Escape rooms",
            "entertainment.escape_game",
            listOf("escape room", "escape game")
        ),
        Group(
            "Mini golf",
            "entertainment.miniature_golf",
            listOf("miniature golf", "putt putt")
        ),
        Group(
            "Amusement parks",
            "entertainment.theme_park",
            listOf("amusement park", "theme park")
        ),
        Group(
            "Water parks",
            "entertainment.water_park",
            listOf("water park")
        ),
        Group(
            "Trampoline parks",
            "entertainment.activity_park.trampoline",
            listOf("trampoline park", "trampoline")
        ),
        Group(
            "Climbing parks",
            "entertainment.activity_park.climbing",
            listOf("climbing park", "climbing")
        ),
        Group(
            "Planetariums",
            "entertainment.planetarium",
            listOf("planetarium")
        ),
        Group(
            "Nightclubs",
            "adult.nightclub",
            listOf("nightclub", "night club", "dance club")
        ),
        Group(
            "Event venues",
            "activity.events_venue",
            listOf("event venue", "event space", "venue")
        ),
        Group(
            "Community centers",
            "activity.community_center",
            listOf("community center", "community centre")
        ),
        Group(
            "Gyms",
            "sport.fitness",
            listOf("gym", "fitness center", "fitness centre", "workout")
        ),
        Group(
            "Swimming pools",
            "sport.swimming_pool",
            listOf("swimming pool", "pool", "public pool")
        ),
        Group(
            "Ice rinks",
            "sport.ice_rink",
            listOf("ice rink", "ice skating", "skating rink")
        ),
        Group(
            "Skate parks",
            "sport.skateboard",
            listOf("skate park", "skatepark", "skateboarding")
        ),
        Group(
            "Golf courses",
            "sport.golf_course",
            listOf("golf", "golf course")
        ),
        Group(
            "Sports centers",
            "sport.sports_centre",
            listOf("sports center", "sports centre", "recreation center")
        ),
        Group(
            "Stadiums",
            "sport.stadium",
            listOf("stadium", "arena")
        ),
        Group(
            "Spas",
            "leisure.spa,service.beauty.spa",
            listOf("spa", "day spa")
        ),
        Group(
            "Clothing stores",
            "commercial.clothing",
            listOf("clothing store", "clothes shop", "fashion store")
        ),
        Group(
            "Convenience stores",
            "commercial.convenience",
            listOf("convenience store", "corner store")
        ),
        Group(
            "Florists",
            "commercial.florist",
            listOf("florist", "flower shop")
        ),
        Group(
            "Pet stores",
            "commercial.pet",
            listOf("pet store", "pet shop")
        ),
        Group(
            "Toy stores",
            "commercial.toy_and_game",
            listOf("toy store", "game store")
        ),
        Group(
            "Libraries",
            "education.library",
            listOf("library", "public library")
        ),
        Group(
            "Hotels",
            "accommodation.hotel",
            listOf("hotel", "lodging")
        ),
        Group(
            "Coffee shops",
            "catering.cafe",
            listOf(
                "cafe", "cafes", "coffee", "coffee shop", "coffee shops"
            )
        ),
        Group(
            "Restaurants",
            "catering.restaurant",
            listOf(
                "restaurant", "restaurants", "food", "dining"
            )
        ),
        Group(
            "Burgers",
            "catering.restaurant.burger,catering.fast_food.burger",
            listOf(
                "burger", "burgers", "hamburger", "hamburgers",
                "burger joint", "burger place", "burger restaurant",
                "cheeseburger", "cheeseburgers"
            )
        ),
        Group(
            "Pizza",
            "catering.restaurant.pizza,catering.fast_food.pizza",
            listOf(
                "pizza", "pizzeria", "pizzerias", "pizza place"
            )
        ),
        Group(
            "Parks",
            "leisure.park",
            listOf(
                "park", "parks"
            )
        ),
        Group(
            "Playgrounds",
            "leisure.playground",
            listOf(
                "playground", "playgrounds"
            )
        ),
        Group(
            "Picnic areas",
            "leisure.picnic",
            listOf(
                "picnic", "picnic spot", "picnic area"
            )
        ),
        Group(
            "Movie theaters",
            "entertainment.cinema",
            listOf(
                "movie", "movies", "cinema",
                "movie theater", "movie theatre"
            )
        ),
        Group(
            "Bowling",
            "entertainment.bowling_alley",
            listOf(
                "bowling", "bowling alley"
            )
        ),
        Group(
            "Museums",
            "entertainment.museum",
            listOf(
                "museum", "museums"
            )
        ),
        Group(
            "Arcades",
            "entertainment.amusement_arcade",
            listOf(
                "arcade", "arcades"
            )
        ),
        Group(
            "Aquariums",
            "entertainment.aquarium",
            listOf(
                "aquarium", "aquariums"
            )
        ),
        Group(
            "Zoos",
            "entertainment.zoo",
            listOf(
                "zoo", "zoos"
            )
        ),
        Group(
            "Art galleries",
            "entertainment.culture.gallery",
            listOf(
                "gallery", "art gallery"
            )
        ),
        Group(
            "Theaters",
            "entertainment.culture.theatre",
            listOf(
                "theater", "theatre", "performing arts"
            )
        ),
        Group(
            "Shopping malls",
            "commercial.shopping_mall",
            listOf(
                "mall", "shopping mall"
            )
        ),
        Group(
            "Grocery stores",
            "commercial.supermarket",
            listOf(
                "supermarket", "grocery", "grocery store"
            )
        ),
        Group(
            "Bookstores",
            "commercial.books",
            listOf(
                "bookstore", "book store", "books"
            )
        ),
        Group(
            "Sushi",
            "catering.restaurant.sushi",
            listOf(
                "sushi", "sushi restaurant"
            )
        ),
        Group(
            "Mexican food",
            "catering.restaurant.mexican",
            listOf(
                "mexican", "mexican food"
            )
        ),
        Group(
            "Italian food",
            "catering.restaurant.italian",
            listOf(
                "italian", "italian food"
            )
        ),
        Group(
            "Chinese food",
            "catering.restaurant.chinese",
            listOf(
                "chinese", "chinese food"
            )
        ),
        Group(
            "Indian food",
            "catering.restaurant.indian",
            listOf(
                "indian", "indian food"
            )
        ),
        Group(
            "Bubble tea",
            "catering.cafe.bubble_tea",
            listOf(
                "boba", "bubble tea"
            )
        ),
        Group(
            "Ice cream",
            "catering.ice_cream,catering.cafe.ice_cream",
            listOf(
                "ice cream", "ice cream shop"
            )
        ),
        Group(
            "Fast food",
            "catering.fast_food",
            listOf(
                "fast food", "takeout"
            )
        ),
        Group(
            "Tacos",
            "catering.restaurant.tacos,catering.fast_food.tacos",
            listOf(
                "taco", "tacos", "taco place"
            )
        ),
        Group(
            "Sandwiches",
            "catering.restaurant.sandwich,catering.fast_food.sandwich",
            listOf(
                "sandwich", "sandwiches", "sandwich shop"
            )
        ),
        Group(
            "Wings",
            "catering.restaurant.wings,catering.fast_food.wings",
            listOf(
                "wings", "chicken wings"
            )
        ),
        Group(
            "Ramen",
            "catering.restaurant.ramen,catering.fast_food.ramen",
            listOf(
                "ramen", "ramen shop"
            )
        ),
        Group(
            "Barbecue",
            "catering.restaurant.barbecue",
            listOf(
                "bbq", "barbecue", "barbeque"
            )
        )


    )

    val suggestions: List<String> = groups
        .flatMap {
            listOf(it.label) + it.aliases
        }
        .distinctBy {
            normalize(it)
        }
        .sortedBy {
            normalize(it)
        }

    fun resolve(query: String): Match {
        val cleaned = query
            .trim()
            .replace(
                Regex("(?i)\\s+near\\s+me$"),
                ""
            )
            .trim()

        val key = normalize(cleaned)

        val group = groups.firstOrNull { group ->
            normalize(group.label) == key ||
                    group.aliases.any {
                        normalize(it) == key
                    }
        }

        return if (group != null) {
            Match(
                categories = group.categories,
                businessName = "",
                label = group.label
            )
        } else {
            Match(
                categories =
                    "accommodation,activity,adult,beach,camping,catering," +
                            "commercial,education,entertainment,leisure,service,sport,tourism",
                businessName = cleaned
            )
        }
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(
            value,
            Normalizer.Form.NFD
        )
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}