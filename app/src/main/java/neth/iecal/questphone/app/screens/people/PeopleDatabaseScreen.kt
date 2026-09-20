package neth.iecal.questphone.app.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import neth.iecal.questphone.app.theme.LocalCustomTheme

// ── Data ──────────────────────────────────────────────────────────────────────

enum class FactCategory(val label: String) {
    SCIENCE("Science"),
    MYTH("Myth Busted"),
    JOKE("Joke"),
    DISCOVERY("Discovery"),
    FAMOUS("Famous People")
}

data class Fact(val category: FactCategory, val text: String)

private val ALL_FACTS = listOf(

    // SCIENCE
    Fact(FactCategory.SCIENCE, "Honey never spoils. Archaeologists have found 3000-year-old honey in Egyptian tombs that was still edible."),
    Fact(FactCategory.SCIENCE, "A day on Venus is longer than a year on Venus. It rotates so slowly that it completes one orbit around the Sun before finishing one rotation."),
    Fact(FactCategory.SCIENCE, "Hot water can freeze faster than cold water under certain conditions. This is called the Mpemba effect."),
    Fact(FactCategory.SCIENCE, "There are more possible iterations of a game of chess than there are atoms in the observable universe."),
    Fact(FactCategory.SCIENCE, "Bananas are slightly radioactive because they contain potassium-40, a naturally occurring radioactive isotope."),
    Fact(FactCategory.SCIENCE, "The human body contains enough carbon to make about 900 pencils."),
    Fact(FactCategory.SCIENCE, "Octopuses have three hearts, blue blood, and nine brains — one central brain and one in each arm."),
    Fact(FactCategory.SCIENCE, "Light takes about 8 minutes and 20 seconds to travel from the Sun to Earth."),
    Fact(FactCategory.SCIENCE, "Water can exist as a solid, liquid, and gas simultaneously at a specific temperature and pressure called the triple point."),
    Fact(FactCategory.SCIENCE, "The average human brain generates about 20 watts of electrical power — enough to power a dim light bulb."),
    Fact(FactCategory.SCIENCE, "A teaspoon of neutron star material would weigh about 10 million tons on Earth."),
    Fact(FactCategory.SCIENCE, "Sharks are older than trees. Sharks have existed for about 450 million years while trees only appeared 350 million years ago."),
    Fact(FactCategory.SCIENCE, "Your stomach produces a new layer of mucus every two weeks to avoid digesting itself."),
    Fact(FactCategory.SCIENCE, "The speed of light is exactly 299,792,458 metres per second. It is a defined constant, not a measured one."),
    Fact(FactCategory.SCIENCE, "There are more bacteria in your mouth right now than there are people on Earth."),
    Fact(FactCategory.SCIENCE, "DNA is so tightly packed that if you stretched out all the DNA in your body it would reach the Sun and back about 300 times."),
    Fact(FactCategory.SCIENCE, "Dying stars can create conditions that last for trillions of years — far longer than the current age of the universe."),
    Fact(FactCategory.SCIENCE, "The universe is about 13.8 billion years old, but the observable universe spans 93 billion light years due to expansion."),
    Fact(FactCategory.SCIENCE, "Crows can recognize human faces and hold grudges against people who have wronged them."),
    Fact(FactCategory.SCIENCE, "Sound cannot travel in space because there is no medium for the waves to pass through."),

    // MYTH BUSTED
    Fact(FactCategory.MYTH, "We do not use only 10 percent of our brains. Brain scans show that virtually all areas of the brain are active almost all the time."),
    Fact(FactCategory.MYTH, "Goldfish do not have a 3-second memory. Studies show they can remember things for months and can even be trained."),
    Fact(FactCategory.MYTH, "Lightning does strike the same place twice. The Empire State Building is struck by lightning about 25 times per year."),
    Fact(FactCategory.MYTH, "Eating carrots does not give you night vision. This myth was spread by British intelligence in World War 2 to hide their use of radar."),
    Fact(FactCategory.MYTH, "Hair and nails do not keep growing after death. The skin dehydrates and shrinks, making them appear longer."),
    Fact(FactCategory.MYTH, "You do not need to wait 24 hours before reporting a missing person. You can report it immediately."),
    Fact(FactCategory.MYTH, "Bulls are not enraged by the color red. They are color-blind to red. It is the movement of the matador's cape that agitates them."),
    Fact(FactCategory.MYTH, "Napoleon was not unusually short. He was about 5 feet 7 inches tall, which was average for his time. The myth came from a confusion between French and English units of measurement."),
    Fact(FactCategory.MYTH, "Humans do not have only five senses. Scientists now recognize at least 9, including balance, temperature, pain, and time perception."),
    Fact(FactCategory.MYTH, "Swallowed chewing gum does not stay in your stomach for 7 years. It passes through your digestive system within a few days."),
    Fact(FactCategory.MYTH, "The Great Wall of China is not visible from space with the naked eye. This has been confirmed by astronauts including Chinese astronaut Yang Liwei."),
    Fact(FactCategory.MYTH, "Chameleons do not change color to match their surroundings. They change color to communicate mood and temperature regulation."),
    Fact(FactCategory.MYTH, "We do not lose most of our body heat through our head. Heat loss is proportional to surface area exposed."),
    Fact(FactCategory.MYTH, "Alcohol does not warm you up. It only creates a sensation of warmth by dilating blood vessels while actually lowering core body temperature."),
    Fact(FactCategory.MYTH, "Sugar does not make children hyperactive. Multiple double-blind studies have found no link between sugar intake and hyperactivity."),
    Fact(FactCategory.MYTH, "Waking a sleepwalker is not dangerous. It may disorient them briefly but it will not harm them."),
    Fact(FactCategory.MYTH, "Toilet water in the Southern Hemisphere does not spin in the opposite direction. The Coriolis effect is far too weak to affect toilets or sinks."),
    Fact(FactCategory.MYTH, "Humans did not evolve from chimpanzees. Humans and chimps share a common ancestor but evolved along separate branches."),
    Fact(FactCategory.MYTH, "Diamonds are not formed from coal. Most diamonds formed deep in Earth's mantle long before large coal deposits existed."),
    Fact(FactCategory.MYTH, "Cats do not always land on their feet. They have a righting reflex but can still be injured from falls."),

    // JOKES
    Fact(FactCategory.JOKE, "I told my computer I needed a break. Now it will not stop sending me vacation ads."),
    Fact(FactCategory.JOKE, "Why do scientists rarely tell jokes? Because they are afraid the jokes will not get a reaction."),
    Fact(FactCategory.JOKE, "A photon checks into a hotel. The bellboy asks if he has any luggage. The photon replies: No, I am traveling light."),
    Fact(FactCategory.JOKE, "I am reading a book about gravity. It is impossible to put down."),
    Fact(FactCategory.JOKE, "Why did the math book look so sad? Because it had too many problems."),
    Fact(FactCategory.JOKE, "A neutron walks into a bar and asks how much for a beer. The bartender says: For you, no charge."),
    Fact(FactCategory.JOKE, "I asked the librarian if they had books about paranoia. She whispered: They are right behind you."),
    Fact(FactCategory.JOKE, "Why do programmers prefer dark mode? Because light attracts bugs."),
    Fact(FactCategory.JOKE, "I tried to write a joke about infinity. But I did not know where to end."),
    Fact(FactCategory.JOKE, "My WiFi password is the last 8 digits of pi. Good luck."),
    Fact(FactCategory.JOKE, "Why did the scarecrow win an award? Because he was outstanding in his field."),
    Fact(FactCategory.JOKE, "I told a joke about construction once. I am still working on it."),
    Fact(FactCategory.JOKE, "Did you hear about the mathematician who is afraid of negative numbers? He will stop at nothing to avoid them."),
    Fact(FactCategory.JOKE, "Why can you never trust an atom? Because they make up everything."),
    Fact(FactCategory.JOKE, "I have a joke about time travel but you did not like it."),
    Fact(FactCategory.JOKE, "What do you call a sleeping dinosaur? A dino-snore."),
    Fact(FactCategory.JOKE, "Two wifi antennas got married. The ceremony was not much but the reception was excellent."),
    Fact(FactCategory.JOKE, "I would tell you a chemistry joke but I know I would not get a reaction."),
    Fact(FactCategory.JOKE, "Why does the Sun never need to go to university? Because it already has a million degrees."),
    Fact(FactCategory.JOKE, "I started a band called 999 Megabytes. We have not had a gig yet."),

    // DISCOVERY
    Fact(FactCategory.DISCOVERY, "In 2017, scientists discovered a new organ in the human body called the interstitium — a network of fluid-filled spaces beneath the skin."),
    Fact(FactCategory.DISCOVERY, "In 2012, a species of jellyfish called Turritopsis dohrnii was confirmed to be biologically immortal — it can revert to its juvenile state after reaching adulthood."),
    Fact(FactCategory.DISCOVERY, "In 2023, scientists confirmed the existence of a gravitational wave background — a constant hum of gravitational waves permeating the universe."),
    Fact(FactCategory.DISCOVERY, "The microwave oven was invented by accident in 1945 when engineer Percy Spencer noticed a chocolate bar in his pocket had melted while he was working with radar equipment."),
    Fact(FactCategory.DISCOVERY, "Penicillin was discovered by Alexander Fleming in 1928 when he noticed mold had killed bacteria on a petri dish he left out by accident."),
    Fact(FactCategory.DISCOVERY, "X-rays were discovered by Wilhelm Rontgen in 1895. He took the first X-ray image of his wife's hand and she reportedly said it made her see her own death."),
    Fact(FactCategory.DISCOVERY, "The element helium was discovered on the Sun before it was found on Earth, using spectroscopy during a solar eclipse in 1868."),
    Fact(FactCategory.DISCOVERY, "Scientists discovered in 2020 that trees communicate and share nutrients through underground fungal networks called the Wood Wide Web."),
    Fact(FactCategory.DISCOVERY, "In 1991, ice man Otzi was discovered in the Alps, naturally preserved for 5300 years. He still had his last meal in his stomach."),
    Fact(FactCategory.DISCOVERY, "Velcro was invented in 1941 by Swiss engineer George de Mestral after he noticed how burr seeds clung to his dog's fur during a walk."),
    Fact(FactCategory.DISCOVERY, "The Dead Sea Scrolls, discovered in 1947, are the oldest known manuscripts of the Hebrew Bible, dating back over 2000 years."),
    Fact(FactCategory.DISCOVERY, "Scientists discovered in 2019 that the first image of a black hole captured belonged to M87 galaxy and was 6.5 billion times the mass of our Sun."),
    Fact(FactCategory.DISCOVERY, "Post-it Notes were invented from a failed attempt to make a super-strong adhesive. The accidental weak adhesive turned out to be far more useful."),
    Fact(FactCategory.DISCOVERY, "In 2015, scientists confirmed the existence of gravitational waves — ripples in spacetime — first predicted by Einstein 100 years earlier."),
    Fact(FactCategory.DISCOVERY, "The cosmic microwave background radiation, discovered accidentally in 1965 by Penzias and Wilson, is the leftover heat from the Big Bang."),
    Fact(FactCategory.DISCOVERY, "Scientists discovered that octopuses edit their own RNA in real time, allowing them to adapt to temperature changes without changing their DNA."),
    Fact(FactCategory.DISCOVERY, "In 2022, the James Webb Space Telescope captured images of galaxies formed just 300 million years after the Big Bang."),
    Fact(FactCategory.DISCOVERY, "The world's oldest known living organism is a seagrass clone in Australia estimated to be 80,000 years old and covering 200 square kilometres."),
    Fact(FactCategory.DISCOVERY, "Scientists discovered that the human appendix is not useless — it serves as a reservoir for beneficial gut bacteria after intestinal infections."),
    Fact(FactCategory.DISCOVERY, "In 2024, researchers confirmed that dark matter makes up about 27 percent of the universe but has never been directly detected."),

    // FAMOUS PEOPLE
    Fact(FactCategory.FAMOUS, "Isaac Newton developed the laws of motion and universal gravitation while under quarantine during the Great Plague of London in 1665 at age 23."),
    Fact(FactCategory.FAMOUS, "Albert Einstein failed his first university entrance exam. He was denied admission to ETH Zurich at age 15 and had to study for another year."),
    Fact(FactCategory.FAMOUS, "Nikola Tesla could memorize entire books and could speak eight languages. He claimed to never sleep more than two hours a night."),
    Fact(FactCategory.FAMOUS, "Marie Curie is the only person to win Nobel Prizes in two different sciences — Physics in 1903 and Chemistry in 1911."),
    Fact(FactCategory.FAMOUS, "Leonardo da Vinci could write with one hand and draw with the other simultaneously. He also wrote mirror-script in his notebooks."),
    Fact(FactCategory.FAMOUS, "Stephen Hawking was diagnosed with ALS at 21 and given two years to live. He survived for 55 more years and made some of the greatest contributions to theoretical physics."),
    Fact(FactCategory.FAMOUS, "Srinivasa Ramanujan had almost no formal training in mathematics but independently rediscovered thousands of mathematical results and filled notebooks with theorems that mathematicians are still proving today."),
    Fact(FactCategory.FAMOUS, "Charles Darwin spent 8 years studying barnacles before publishing On the Origin of Species. He wanted to understand every detail before making his claim."),
    Fact(FactCategory.FAMOUS, "Ada Lovelace wrote the first algorithm intended for a machine in the 1840s, making her the world's first computer programmer — a century before the first computer existed."),
    Fact(FactCategory.FAMOUS, "Galileo Galilei was placed under house arrest for the last nine years of his life for supporting the heliocentric model of the solar system."),
    Fact(FactCategory.FAMOUS, "Vincent van Gogh only sold one painting during his entire lifetime. Today his works sell for over 80 million dollars each."),
    Fact(FactCategory.FAMOUS, "Alan Turing broke Nazi Germany's Enigma code, potentially shortening World War 2 by two years and saving millions of lives, yet was later prosecuted by the British government for his sexuality."),
    Fact(FactCategory.FAMOUS, "Nikola Tesla sold his AC power patents to George Westinghouse for far less than they were worth just so that electricity could be affordable for everyone."),
    Fact(FactCategory.FAMOUS, "Richard Feynman, Nobel Prize-winning physicist, also played bongo drums professionally and was an expert safe-cracker who broke into safes at Los Alamos during the Manhattan Project."),
    Fact(FactCategory.FAMOUS, "Albert Einstein offered his Nobel Prize money to his first wife Mileva as part of their divorce settlement — before he had actually won it."),
    Fact(FactCategory.FAMOUS, "APJ Abdul Kalam, the Missile Man of India, came from a very poor family in Rameswaram and used to sell newspapers as a child before becoming the President of India."),
    Fact(FactCategory.FAMOUS, "Elon Musk taught himself programming at age 10, sold his first game Blastar at age 12 for 500 dollars to a computing magazine."),
    Fact(FactCategory.FAMOUS, "Subrahmanyan Chandrasekhar calculated the maximum mass of a white dwarf star on a ship voyage from India to England at age 19. His result, the Chandrasekhar limit, is fundamental to astrophysics."),
    Fact(FactCategory.FAMOUS, "Isaac Asimov wrote or edited over 500 books in his lifetime, covering almost every category of the Dewey Decimal System."),
    Fact(FactCategory.FAMOUS, "Nikola Tesla once claimed he could split the Earth in two with the right frequency of mechanical resonance, based on his earthquake machine experiments in New York City.")
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleDatabaseScreen(navController: NavController) {
    val theme = LocalCustomTheme.current
    val primary = theme.getRootColorScheme().primary
    val background = theme.getRootColorScheme().background
    val surface = theme.getRootColorScheme().surface
    val onBackground = theme.getRootColorScheme().onBackground
    val onSurface = theme.getRootColorScheme().onSurface

    var selectedCategory by remember { mutableStateOf<FactCategory?>(null) }
    var seed by remember { mutableStateOf(0) }

    val displayed = remember(selectedCategory, seed) {
        val pool = if (selectedCategory == null) ALL_FACTS
                   else ALL_FACTS.filter { it.category == selectedCategory }
        pool.shuffled().take(20)
    }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Knowledge Feed", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "${ALL_FACTS.size} facts across 5 categories",
                            fontSize = 11.sp,
                            color = onBackground.copy(alpha = 0.5f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { seed++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Shuffle")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background)
            )
        },
        containerColor = background
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Category filter row
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        CategoryChip(
                            label = "All",
                            selected = selectedCategory == null,
                            primary = primary,
                            onBackground = onBackground,
                            onClick = { selectedCategory = null }
                        )
                    }
                    items(FactCategory.entries) { cat ->
                        CategoryChip(
                            label = cat.label,
                            selected = selectedCategory == cat,
                            primary = primary,
                            onBackground = onBackground,
                            onClick = { selectedCategory = cat }
                        )
                    }
                }
            }

            // Fact cards
            items(displayed, key = { it.text }) { fact ->
                FactCard(
                    fact = fact,
                    primary = primary,
                    surface = surface,
                    onSurface = onSurface,
                    onBackground = onBackground
                )
            }

            // Shuffle button at bottom
            item {
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { seed++ },
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle New Facts", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    primary: Color,
    onBackground: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) primary else primary.copy(alpha = 0.1f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else onBackground.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun FactCard(
    fact: Fact,
    primary: Color,
    surface: Color,
    onSurface: Color,
    onBackground: Color
) {
    val categoryColor = when (fact.category) {
        FactCategory.SCIENCE   -> Color(0xFF0284C7)
        FactCategory.MYTH      -> Color(0xFFDC2626)
        FactCategory.JOKE      -> Color(0xFF16A34A)
        FactCategory.DISCOVERY -> Color(0xFF7C3AED)
        FactCategory.FAMOUS    -> Color(0xFFD97706)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Category label
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(categoryColor.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = fact.category.label.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = categoryColor
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = fact.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = onSurface,
                lineHeight = 22.sp
            )
        }
    }
}
