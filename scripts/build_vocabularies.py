#!/usr/bin/env python3
"""Build separated offline vocabulary files. File order is frequency rank."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "app/src/main/res/raw"


def unique(words):
    seen = set()
    out = []
    for word in words:
        item = word.strip()
        if not item or item.startswith("#") or " " in item:
            continue
        key = item.lower()
        if key in seen:
            continue
        seen.add(key)
        out.append(item)
    return out


def write_list(path, header, words):
    lines = [header, ""]
    lines.extend(unique(words))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return len(unique(words))


ENGLISH_PRIORITY = """
the be to of and a in that have i it for not on with he hello help her here hear
as you do at this but his by from they we say she or an will my one all would
are was were am been being
there their what so if about who get which when make can like time no just him
know take people into year your good some could them see other than then now
look only come its over think also back after use two how our work first well
way even new want because any these give day most us
where why who whose whom
okay ok please thanks thank sorry welcome
morning afternoon evening night today tomorrow yesterday
now later soon always never sometimes again already still
very really just only more most some any all many much few
one two three four five six seven eight nine ten first last next
new old big small long short high low right wrong same different
nice happy sad busy free ready sure fine
love like want need know think feel see look watch hear say tell ask answer
call send get give take make do done go come leave stay wait start stop try
use work play read write learn study eat drink sleep walk run meet find keep
put bring open close can could will would should may might must have has had
school college class office job meeting family friend friends name place house
room road city country food water tea coffee money time day week month year
phone mobile computer laptop internet online email message chat photo video
music car bus bike world people thing question problem idea word language
English Nepali
""".split()

ENGLISH_COMMON = """
about above accept accident account across act action activity actor actual add
address adult advance advice advise affect afraid after afternoon again against
age agency agent ago agree agreement ahead air airport album alive allow almost
alone along already also although always amazing among amount ancient angry
animal announce another answer anxiety anyone anything anyway anywhere apart
apartment appear apple apply appointment area argue argument arm army around
arrive art article artist ashamed ask asleep attack attempt attend attention
attitude attorney attract audience author available average avoid awake award
aware away awesome baby back background bag bake balance ball banana band bank
bar barely base baseball basic basis basket basketball bath bathroom battery
battle beach bean bear beard beautiful beauty because become bed bedroom bee
beef beer before begin beginning behave behavior behind being belief believe
bell belong below belt bench bend benefit beside best better between beyond
bicycle bill billion bind bird birth birthday bit bite bitter black blame blank
blind block blood blow blue board boat body boil bomb bond bone book boom boot
border born borrow boss both bother bottle bottom bowl box boy boyfriend brain
branch brand bread break breakfast breast breath breathe brick bridge brief
bright brilliant bring broad brother brown brush budget build building bullet
bunch burn burst bus business busy butter button buy buyer cabin cabinet cable
cake calculate calendar call camera camp campaign campus can cancel cancer
candidate candy cap capable capacity capital captain capture car card care
career careful carry case cash cat catch category cause ceiling celebrate
celebration cell cent center central century ceremony certain certainly chain
chair challenge champion chance change channel chapter character charge charity
chart chase cheap check cheek cheese chef chemical chest chicken chief child
childhood chip chocolate choice choose church cigarette circle citizen city
civil claim class classic classroom clean clear clearly clerk client climate
climate climb clinic clock close closer cloth clothes cloud club clue coach
coal coast coat code coffee coin cold collar colleague collect collection
college color column combination combine come comfort comfortable command
comment commercial commission commit commitment committee common communicate
communication community company compare comparison compete competition
complain complete completely complex computer concentrate concern concert
conclude condition conference confidence confident confirm conflict confuse
congress connect connection conscious consider consist constant construct
construction consume consumer contact contain container content contest
context continue contract contrast contribute contribution control conversation
convert cook cookie cooking cool copy corner corporate correct cost cotton
couch could council count counter country county couple courage course court
cousin cover cow crack craft crash crazy cream create creation creative
creature credit crew crime criminal crisis criteria critic critical criticism
crop cross crowd crucial cry cultural culture cup curious current currently
curriculum curtain custom customer cut cute cycle dad daily damage dance danger
dangerous dare dark data date daughter dawn day dead deal dealer dear death
debate debt decade decide decision deck declare decline deep deeply deer
defeat defend defense deficit define definitely definition degree delay
delicious deliver delivery demand democracy democrat democratic demonstrate
demonstration dentist deny department depend dependent depending depict
depression depth deputy derive describe description desert deserve design
designer desire desk desperate despite destroy destruction detail detailed
detect determine develop development device devote dialogue die diet differ
difference different differently difficult difficulty dig digital dimension
dining dinner direct direction directly director dirt dirty disability
disagree disappear disaster discipline discount discover discovery
discrimination discuss discussion disease dish dismiss disorder display
dispute distance distant distinct distinction distinguish distribute
distribution district disturb divide division divorce dna doctor document dog
dollar domestic dominate door double doubt down downtown dozen draft drag drama
dramatic draw drawing dream dress drink drive driver drop drug dry due during
dust duty each eager ear early earn earth easily east eastern easy eat
economic economics economy edge edition editor educate education educational
educator effect effective effectively efficiency efficient effort egg eight
either elderly elect election electric electrical electricity electronic
element elementary elephant else elsewhere email emerge emergency emission
emotion emotional emphasis emphasize empire employ employee employer
employment empty enable encounter encourage end enemy energy enforcement
engage engine engineer engineering english enhance enjoy enormous enough
ensure enter entire entirely entrance entry environment environmental episode
equal equally equipment era error escape especially essay essential establish
establishment estate estimate etc ethics ethnic evaluate evaluation even
evening event eventually ever every everybody everyday everyone everything
everywhere evidence evolution evolve exact exactly exam examination examine
example exceed excellent except exception exchange excited excitement exciting
excuse executive exercise exhibit exhibition exist existence existing expand
expansion expect expectation expense expensive experience experiment expert
explain explanation explode explore explosion export expose exposure express
expression extend extension extensive extent external extra extraordinary
extreme extremely eye fabric face facility fact factor factory faculty fail
failure fair fairly faith fall false familiar family famous fan fancy
fantastic far farm farmer fashion fast fat fate father fault favor favorite
fear feature federal fee feed feel feeling fellow female fence festival few
fewer fiber fiction field fifteen fifty fight fighter fighting figure file
fill film final finally finance financial find finding fine finger finish fire
firm first fish fishing fit fitness five fix flag flame flat flavor flesh
flight float flood floor flour flow flower fly focus folk follow following
food foot football for force foreign forest forever forget form formal
formation former formula forth fortune forty forward found foundation founder
four fourth frame framework free freedom freeze french frequency frequent
frequently fresh friend friendly friendship from front fruit frustration fuel
full fully fun function fund fundamental funding funeral funny furniture
further future gain galaxy gallery game gang gap garage garden garlic gas gate
gather gay gaze gear gender gene general generally generate generation genetic
gentleman gently gesture get ghost giant gift gifted girl girlfriend give
given glad glass global glove go goal god gold golden golf good government
governor grab grade gradually graduate grain grand grandfather grandmother
grant grass grave gray great greatest greatly green grocery ground group grow
growth guarantee guard guess guest guide guilty guitar gun guy habit habitat
hair half hall hand handful handle hang happen happily happy hard hardly
hardware harm hat hate have he head headline headquarters health healthy hear
hearing heart heat heaven heavily heavy heel height helicopter hell hello help
helpful her here heritage hero hers herself hey hi hide high highlight highly
highway hill him himself hip hire his historian historic historical history
hit hold hole holiday holy home homeless honest honey honor hook hope
hopefully horizon horror horse hospital host hot hotel hour house household
housing how however huge human humor hundred hungry hunter hunting hurt
husband hypothesis i ice idea ideal identification identify identity ie if
ignore ill illegal illness illustrate image imagination imagine immediate
immediately immigrant immigration impact implement implication imply
importance important impose impossible impress impression impressive improve
improvement in incentive incident include including income incorporate
increase increased increasing increasingly incredible indeed independence
independent index indian indicate indication individual industrial industry
infant infection inflation influence inform information ingredient initial
initially initiative injury inner innocent inquiry inside insight insist
inspire install instance instead institution institutional instruction
instructor instrument insurance intellectual intelligence intend intense
intensity intention interaction interest interested interesting internal
international internet interpret interpretation intervention interview into
introduce introduction invasion invest investigate investigation investigator
investment investor invite involve involved involvement iraqi irish iron
islamic island isolate issue it italian item its itself jacket jail japanese
jet jew jewish job join joint joke journal journalist journey joy judge
judgment juice jump junior jury just justice justify keep key kick kid kill
killer killing kind king kiss kitchen knee knife knock know knowledge known
lab label labor laboratory lack lady lake land landscape language lap large
largely last late later latter laugh launch law lawn lawsuit lawyer lay layer
lead leader leadership leading leaf league lean learn learning least leather
leave left leg legacy legal legend legislation legitimate lemon length less
lesson let letter level liability liberal library license lid lie life
lifestyle lifetime lift light like likely limit limitation limited line link
lip list listen literally literary literature little live liver living load
loan local locate location lock long long-term look loose lose loss lost lot
lots loud love lovely lover low lower luck lucky lunch lung machine mad
magazine magic mail main mainly maintain maintenance major majority make maker
makeup male mall man manage management manager manner manufacturer
manufacturing many map margin mark market marketing marriage married marry
mask mass massive master match material math matter may maybe mayor me meal
mean meaning meanwhile measure measurement meat mechanism media medical
medication medicine medium meet meeting member membership memory mental
mention menu mere merely mess message metal meter method mexican middle might
military milk million mind mine minister minor minority minute miracle mirror
miss missile mission mistake mix mixture mm mobile mode model moderate modern
modest mom moment money monitor month mood moon moral more moreover morning
mortgage most mostly mother motion motivation motor mount mountain mouse mouth
move movement movie mr mrs much multiple murder muscle museum music musical
musician muslim must mutual my myself mystery myth nail name narrative narrow
nation national native natural naturally nature near nearby nearly necessarily
necessary neck need negative negotiate negotiation neighbor neighborhood
neither nerve nervous net network never nevertheless new newly news newspaper
next nice night nine no nobody nod noise nomination none nonetheless nor
normal normally north northern nose not note nothing notice notion novel now
nowhere nuclear number numerous nurse nut object objective obligation
observation observe observer obtain obvious obviously occasion occasionally
occupation occupy occur ocean odd odds of off offense offensive offer office
officer official often oh oil ok okay old olympic on once one ongoing onion
online only onto open opening operate operating operation operator opinion
opponent opportunity oppose opposite opposition option or orange orbit order
ordinary organic organization organize orientation origin original originally
other others otherwise ought our ours ourselves out outcome outside oven over
overall overcome overlook owe own owner oxygen pace pack package page pain
painful paint painter painting pair pale palestinian palm pan panel pants
paper parent parental park parking part participant participate participation
particular particularly partly partner partnership party pass passage
passenger passion past patch path patient pattern pause pay payment peace peak
peer penalty people pepper per perceive percent percentage perception perfect
perfectly perform performance perhaps period permanent permission permit
person personal personality personally personnel perspective persuade pet
phase phenomenon philosophy phone photo photograph photographer phrase
physical physically piano pick picture pie piece pile pill pilot pine pink
pipe pitch place plan plane planet planning plant plastic plate platform play
player please pleasure plenty plot plus pm pocket poem poet poetry point pole
police policy political politically politician politics poll pollution pool
poor pop popular population porch port portion portrait portray pose position
positive possess possibility possible possibly post pot potato potential
potentially pound pour poverty powder power powerful practical practice pray
prayer precisely predict prefer preference pregnant preparation prepare
prescription presence present presentation preserve president presidential
press pressure pretend pretty prevent previous previously price pride priest
primarily primary prime principal principle print prior priority prison
prisoner privacy private probably problem procedure proceed process produce
producer product production profession professional professor profile profit
program progress project prominent promise promote promotion prompt proof
proper properly property proportion proposal propose proposed prosecutor
prospect protect protection protein protest proud prove provide provider
province provision psychological psychologist psychology public publication
publicly publish publisher pull punishment purchase pure purpose pursue push
put qualify quality quarter quarterback question quick quickly quiet quietly
quit quite quote race racial radical radio rail rain raise range rank rapid
rapidly rare rarely rate rather rating ratio raw reach react reaction read
reader reading ready real reality realize really reason reasonable rebel
recall receive recent recently recipe recognition recognize recommend
recommendation record recording recover recovery recruit red reduce reduction
refer reference reflect reflection reform refugee refuse regard regarding
regardless regime region regional register regular regularly regulate
regulation reinforce reject relate related relation relationship relative
relatively relax release relevant relief religion religious rely remain
remaining remarkable remember remind remote remove repeat repeatedly replace
reply report reporter represent representation representative republican
reputation request require required requirement research researcher resemble
reservation resident resist resistance resolution resolve resort resource
respect respond respondent response responsibility responsible rest restaurant
restore restriction result retain retire retirement return reveal revenue
review revolution rhythm rice rich rid ride rifle right ring rise risk river
road rock role roll romantic roof room root rope rose rough roughly round
route routine row rub rule run running rural rush russian sacred sad safe
safety sake salad salary sale sales salt same sample sanction sand satellite
satisfaction satisfy sauce save saving say scale scandal scared scenario scene
schedule scheme scholar scholarship school science scientific scientist scope
score scream screen script sea search season seat second secret secretary
section sector secure security see seed seek seem segment seize select
selection self sell senate senator send senior sense sensitive sentence
separate sequence series serious seriously serve service session set setting
settle settlement seven several severe sex sexual sexuality sexually shade
shadow shake shall shape share sharp she sheet shelf shell shelter shift shine
ship shirt shock shoe shoot shooting shop shopping shore short shortly shot
should shoulder shout show shower shrimp shut sick side sigh sight sign
signal significance significant significantly silence silent silver similar
similarly simple simply simply sin since sing singer single sink sir sister
sit site situation six size ski skill skin sky slave sleep slice slide slight
slightly slip slow slowly small smart smell smile smoke smooth snake snow so
soccer social society sock sodium sofa soft software soil solar soldier solid
solution solve some somebody somehow someone something sometimes somewhat
somewhere son song soon sophisticated sorry sort soul sound soup source south
southern soviet space spanish speak speaker special specialist species
specific specifically speech speed spend spending spin spirit spiritual split
spokesman sport spot spread spring square squeeze stability stable staff stage
stair stake stand standard standing star stare start state statement station
statistics status stay steady steal steel step stick still stir stock stomach
stone stop storage store storm story stove straight strange stranger
strategic strategy stream street strength strengthen stress stretch strike
string strip stroke strong strongly structure struggle student studio study
stuff stupid style subject submit subsequent subsequently substance
substantial succeed success successful successfully such sudden suddenly sue
suffer sufficient sugar suggest suggestion suicide suit suitable summer summit
sun super supply support supporter suppose supposed supreme sure surely
surface surgery surprise surprised surprising surprisingly surround survey
survival survive survivor suspect sustain sustainable swear sweep sweet swim
swing switch symbol symptom system table tablespoon tactic tail take tale
talent talk tall tank tap tape target task taste tax taxpayer tea teach
teacher teaching team tear teaspoon technical technique technology teen
teenager telephone telescope television tell temperature temporary ten tend
tendency tennis tension tent term terms terrible territory terror terrorism
terrorist test testify testimony testing text than thank thanks that the
theater their them theme themselves then theology theory therapy there
therefore these they thick thin thing think thinking third thirty this those
though thought thousand threat threaten three throat through throughout throw
thus ticket tide tie tight time tiny tip tire tired tissue title to tobacco
today toe together tomato tomorrow tone tongue tonight too tool tooth top
topic toss total totally touch tough tour tourist toward towards tower town
toy trace track trade tradition traditional traffic tragedy trail train
training transfer transform transformation transition translate translation
transport transportation trap trash travel treat treatment tree tremendous
trend trial tribe trick trip troop tropical trouble truck true truly trust
truth try tube tunnel turn tv twelve twenty twice twin two type typical
typically ugly ultimate ultimately unable uncle under undergo understand
understanding unfortunately uniform union unique unit united universal
universe university unknown unless unlike unlikely until unusual up upon
upper urban urge us use used useful user usual usually utility vacation
valley valuable value variable variation variety various vary vast vegetable
vehicle venture version versus very vessel veteran via victim victory video
view viewer village violate violation violence violent virtually virtue virus
visible vision visit visitor visual vital vitamin vocal voice volume volunteer
vote voter voting vs vulnerable wage wait wake walk wall wander want war
warm warn warning wash waste watch water wave way we weak wealth wealthy
weapon wear weather wedding week weekend weekly weigh weight welcome welfare
well west western wet what whatever wheel when whenever where whereas whether
which while whisper white who whole whom whose why wide widely widespread
wife wild will willing win wind window wine wing winner winter wipe wire
wisdom wise wish with withdraw within without witness woman wonder wonderful
wood wooden word work worker working works workshop world worried worry worth
would wound wrap write writer writing wrong yard yeah year yell yellow yes
yesterday yet yield you young your yours yourself youth zone
""".split()

ENGLISH_CHAT = """
lol lmao rofl brb btw idk imo imho tbh smh omg wtf yup nope nah yeah ya
gonna wanna gotta kinda sorta yeahh haha hehe hmm ugh wow yay oops
asap fyi nvm tho ur pls plz thx ty tysm lmk hmu dnd afk npc vibe
viral meme story reel post comment share follow unfollow like unlike
dm chat group status online offline typing screenshot screenshot
wifi bluetooth charger battery percent login logout password username
download upload install update delete backup cloud drive folder file
link url website browser tab window app apps google maps gmail
youtube facebook instagram whatsapp telegram tiktok snapchat twitter
x reddit discord zoom meet teams slack netflix spotify amazon
swiggy zomato pathao inDrive uber
""".split()

ENGLISH_NEPAL = """
Nepal Nepali Kathmandu Pokhara Lalitpur Bhaktapur Chitwan Lumbini
Everest Himalaya Terai Madhesh Koshi Gandaki Lumbini Karnali Sudurpashchim
Newar Magar Tamang Gurung Rai Limbu Sherpa Tharu
momos dal bhat chiya tempo micro
dashain tihar holi tee teej chhath
rupee rupees
""".split()

ENGLISH_MORE = """
ability able absence absolute absolutely abstract abuse academic accent accept
access accessible accident accidentally accompany accomplish according
accordingly account accountant accounting accuracy accurate accuse achieve
achievement acid acknowledge acquire across act acting action active actively
activist activity actor actress actual actually ad adapt adaptation add added
addition additional additionally address adequate adjust adjustment
administration administrative administrator admire admission admit adolescent
adopt adoption adult advance advanced advantage adventure advertise
advertisement advertising advice advise adviser advisor advocate aesthetic
affair affect affection afford affordable afraid after aftereffect aftermath
afternoon afterward afterwards again against age aged agency agenda agent
aggression aggressive ago agree agreeable agreement agricultural agriculture
ah ahead aid aide aids aim air airborne aircraft airfare airline airplane
airport aisle alarm album alcohol alcoholic alert alien alike alive all
allegation alleged allegedly alley alliance allocate allocation allow allowance
ally almost alone along alongside aloud alphabet already also alter alternate
alternative alternatively although altitude altogether aluminum always amateur
amaze amazed amazing ambassador ambition ambitious ambulance amend amendment
amid among amount amusing analog analogy analysis analyst analyze ancestor
anchor ancient and anecdote angel anger angle angry animal ankle anniversary
announce announcement announcer annoy annual annually anonymous another answer
ant antenna anthem anthropology antibiotic anticipate anxiety anxious any
anybody anyhow anymore anyone anything anyway anywhere apart apartment
apologize apology app apparatus apparent apparently appeal appear appearance
appendix appetite apple applicant application apply appoint appointment
appreciate appreciation approach appropriate approval approve approximately
april arab architect architectural architecture archive area arena argue
argument arise arithmetic arm armed armor army around arrange arrangement
array arrest arrival arrive arrow art article articulate artifact artificial
artist artistic as ash ashamed aside ask asleep aspect assault assemble
assembly assert assess assessment asset assign assignment assist assistance
assistant associate associated association assume assumption assurance assure
astonishing astronaut astronomer astronomy at ate athlete athletic athletics
atlas atmosphere atom atomic attach attachment attack attain attempt attend
attendance attention attitude attorney attract attraction attractive attribute
auction audience audio audit august aunt authentic author authority authorize
auto automatic automatically automobile autonomy autumn availability available
avenue average aviation avoid await awake award aware awareness away awesome
awful awkward axis
backbone backdrop background backpack backup backward backyard bacon
bacteria badge badly badminton bag bagel baggage bake baker bakery balance
balcony bald ball ballet balloon ballot bamboo ban banana band bandage bang
bank banker banking bankrupt bankruptcy banner bar barbecue barely bargain
bark barn barrel barrier base baseball basement basic basically basin basis
basket basketball bass bat batch bath bathe bathroom bathtub battery battle
battlefield bay beach beacon bead beam bean bear beard beast beat beautiful
beautifully beauty because become bed bedroom bee beef beer before beforehand
beg begin beginner beginning behalf behave behavior behavioral behind being
belief believe bell belong below belt bench bend beneath beneficial benefit
beside besides best bet better between beverage beyond bias bicycle bid big
bike bill billion bind biography biological biology bird birth birthday
biscuit bishop bit bite bitter black blackboard blade blame blank blanket
blast blaze bleed blend bless blessing blind blink block blog blogger blond
blonde blood bloody bloom blouse blow blue blueprint blunt board boast boat
body boil boiling bold bomb bomber bombing bond bone bonus book booking
bookmark boom boost boot booth border bored boring born borrow boss both
bother bottle bottom bounce bound boundary bow bowl box boy boyfriend
bracelet brain brake branch brand brass brave bread break breakdown breakfast
breakthrough breast breath breathe breed brick bride bridge brief briefly
bright brilliant bring broad broadband broadcast broadly broccoli brochure
broken broker bronze brother brotherhood brown browse browser bruise brush
brutal bubble buck bucket buddy budget buffalo buffer bug build builder
building bulb bulk bull bullet bulletin bully bump bun bunch bundle bunny
burden bureau burger burial burn burning burst bury bus bush business
businessman businessman busy but butcher butter butterfly button buy buyer
buzz
cabin cabinet cable cactus cafe cafeteria cage cake calculate calculation
calculator calendar calf call callback caller calm calorie camera camp
campaign campus can canal cancel cancer candidate candle candy cane cannot
canon canvas cap capability capable capacity cape capital capitalism captain
caption capture car carbohydrate carbon card cardboard cardiac cardboard
care career careful carefully caregiver cargo carpet carriage carrot carry
cartoon carve case cash casino cast castle casual casually cat catalog
catalogue catch category cater cathedral cattle cause caution cave cease
ceiling celebrate celebration celebrity cell cellar cemetery census cent
center central century ceramic cereal ceremony certain certainly certificate
chain chair chairman chalk challenge chamber champion championship chance
change changing channel chaos chap chapter character characteristic
characterize charge charity charm chart chase cheap cheat check cheek cheer
cheerful cheese chef chemical chemist chemistry chest chew chicken chief
child childhood childish chip chocolate choice choir choose chop chord
chore chorus christian christmas chronic chunk church cigarette cinema circle
circuit circumstance cite citizen citizenship city civic civil civilian
civilization claim clap clarification clarify class classic classical
classification classify classroom clause claw clay clean cleaner cleaning
clear clearly clerk clever click client cliff climate climb climber clinic
clinical clip clock clone close closely closer closet cloth clothes clothing
cloud cloudy club clue cluster clutch coach coal coalition coast coastal
coat cocktail code coffee coffin cognitive coil coin coincidence cold
collaboration collapse collar colleague collect collection collective
collector college collision colonial colony color colorful column combat
combination combine comedy comfort comfortable comic coming command commander
comment commentary commentator commerce commercial commission commissioner
commit commitment committed committee commodity common commonly communicate
communication community commute compact companion company comparable
comparative compare comparison compass compassion compatible compel
compensate compensation compete competence competent competition competitive
competitor compile complain complaint complement complete completely
completion complex complexity compliance complicated compliment comply
component compose composer composition compound comprehensive compress
comprise compromise compute computer conceal concede conceive concentrate
concentration concept conception concern concerned concerning concert
concession conclude conclusion concrete condemn condition conduct conductor
cone conference confess confession confidence confident confidential
configuration confine confirm confirmation conflict confront confrontation
confuse confused confusing confusion congratulate congratulations congress
congressional connect connection conquer conscience conscious consecutively
consensus consent consequence consequently conservation conservative
consider considerable considerably consideration considering consist
consistency consistent consistently consolation consolidate conspiracy
constant constantly constitute constitution constitutional constrain
constraint construct construction consult consultant consultation consume
consumer consumption contact contain container contemplate contemporary
contempt content contest context continent continual continually continue
continued continuing continuous continuously contract contraction contractor
contradiction contrary contrast contribute contribution contributor control
controversial controversy convenience convenient convention conventional
conversation conversion convert convey convict conviction convince cook
cookbook cookie cooking cool cooperate cooperation cooperative coordinate
coordination coordinator cop cope copper copy copyright coral cord core
corn corner corporate corporation correct correctly correlation correspond
correspondence correspondent corresponding corridor corruption cost costly
costume cottage cotton couch could council counsel counseling counselor
count counter counterpart country countryside county couple coupon courage
course court courtesy courtyard cousin cover coverage cow crack craft crash
crate crawl crazy cream create creation creative creativity creator
creature credit crew cricket crime criminal crisis criteria criterion critic
critical critically criticism criticize crop cross crossing crowd crowded
crown crucial cruel cruise crush cry crystal cub cube cucumber cue
cultivate cultural culturally culture cup cupboard cure curiosity curious
curl currency current currently curriculum curtain curve cushion custom
customer cut cute cycle cycling cylinder
""".split()

ENGLISH_NAMES = """
ram sita hari gita krishna shiva laxmi saraswati
suman bikash prakash santosh anish manish roshan
nisha puja sarita sunita ramesh suresh
john mary david sarah michael emma olivia
alex sam ryan lisa anna james robert
aayush aarav anisha pratik prabin sabin sabina
kiran nabin rabin rohit sohan mohan
""".split()


def english_words():
    return unique(
        ENGLISH_PRIORITY
        + ENGLISH_COMMON
        + ENGLISH_MORE
        + ENGLISH_CHAT
        + ENGLISH_NEPAL
        + ENGLISH_NAMES
    )


NEPALI_PRIORITY = """
म मैले मलाई मेरो मेरी हामी हामीले हामीलाई हाम्रो
तिमी तिमीले तिमीलाई तिम्रो तपाईं तपाईंले तपाईंलाई तपाईंको
ऊ उसले उसलाई उसको उनी उनीहरू यो त्यो को कसले कसलाई आफू आफ्नो
हो होइन हैन छ छु छौ छन् थियो थिएँ थियौ थिए
हुन्छ हुँदैन भयो भो हुनुहुन्छ भनेको भन्ने भन्दा भनेर
र अनि तर पनि पछि पहिले फेरि मात्र सँग सँगै लागि लाई ले कि की नि नै त पो रे
के किन कसरी कहाँ कहिले कति कस्तो कस्ती कुन कुनै
हजुर हस् ठीक ठिकै राम्रै पक्कै सायद होला ल लौ
नमस्ते नमस्कार शुभ सुप्रभात बिहान बेलुका रात शुभरात्रि धन्यवाद बधाई स्वागत
माफ कृपया सन्चै सन्चो मजाले खुसी दुःखी
गर्नु गर्न गर्छु गर्छ गर्छौ गर्छन् गरेँ गर्‍यो गरेर गरेको गर्दै
जानु जान्छु जान्छ जान्छन् जाऊ गयो गएँ गयौ
आउनु आउँछु आउँछ आयो आएँ खानु खाना खान्छु खायो खाएँ
सुत्नु बस्नु बोल्नु सुन्नु हेर्नु देख्नु लेख्नु पढ्नु सिक्नु बुझ्नु
सोध्नु दिनु दिन्छु दियो लिनु लिन्छु लियो ल्याऊ पठाऊ पठाउनु
भेट्नु राख्नु चाहन्छु चाहिन्छ पर्छ पर्दैन सक्छु सक्दिनँ मिल्छ लाग्छ लाग्यो
आज भोलि हिजो अहिले चाँडै ढिलो सधैँ एक दुई तीन चार पाँच छः सात आठ नौ दस
धेरै थोरै सबै केही अलि एकदम
काठमाडौं पोखरा नेपाल नेपाली गाउँ सहर घर कोठा बाटो पसल बजार अस्पताल
पैसा रुपैयाँ पानी चिया कफी भात दाल तरकारी रोटी मोमो मिठो नराम्रो राम्रो
ठूलो सानो नयाँ पुरानो सजिलो गाह्रो चिसो तातो सफा माया मन कुरा काम नाम
ठाउँ दिन हप्ता महिना वर्ष समय
रिसाएको रिस दुःख सुख मायालु सुन्दर सही गलत रमाइलो
खबर समाचार जवाफ फोटो भिडियो मेसेज कल अनलाइन अफलाइन मोबाइल कम्प्युटर
इन्टरनेट फेसबुक युट्युब टिकटक पोस्ट कमेण्ट लाइक सेयर फलो स्टाटस
राम सीता गीता हरि कृष्ण शिव सुमन विकास प्रकाश सन्तोष
मान्छे साथी साथीहरू परिवार आमा बुबा बाबा दाइ दिदी भाइ बहिनी छोरा छोरी
""".split()

NEPALI_MORE = """
अब अझ अझै अथवा अन्यत्र अगाडि अगाडी अगाडिबाट अगाडिको अगाडिसम्म
अधिकार अधिवेशन अध्यक्ष अनुसार अपेक्षा अपेक्षित अबस्था अवस्था
असफल असफलता असम्भव असल असली अस्ट्रेलिया
आकाश आगामी आकर्षक आकर्षण आग्रह आचरण आचरणको आजभोलि आजकाल
आधार आधारभूत आध्यात्मिक आनन्द आफैं आफैले आफैंलाई आएपछि आएर
इच्छा इतिहास इतिहासकार इनाम इलाका इस्लाम इस्लामिक
उचित उदाहरण उदय उद्घाटन उद्देश्य उपलब्धि उपलब्ध उपस्थित उपस्थिति
उपाय उपयोग उपयोगी उपहार उमेर उल्लिखित उल्लङ्घन
एकआपसमा एकआपसमा एकआपसमा एकआपसमा एकआपसमा
एकछिन एकपटक एकसाथ एकै एकैछिन एकैपटक
ओरालो ओसारपसार ओसिलो
औजार औपचारिक औषधि औषधालय
कक्षा कमजोर कमसेकम कमी कम्पनी कर करार कर्मचारी कार्यक्रम
कारण कार्य कार्यालय कार्यकर्ता कार्यान्वयन कालो कागज कानुन कानून
किसान किताब किशोरी किशोर कीर्तिपुर कुराकानी कृषि कृषिप्रधान
खेल खेलाडी खेलकुद खोल खोल्नु खोलियो खोज्नु खोजेँ
गम्भीर गन्तव्य गर्मी गाडी गित गीत गुनगुनाउनु गुरु गुनासो
गोल गोलमेच गृहिणी गृहकार्य गृह मन्त्री
घटना घरपरिवार घरेलु घाम घुम्नु घुम्न घुमघाम
चर्को चर्चा चलाख चाल चलचित्र चलिरहेको चासो चिन्ता चिठी चिन्नु
चुनाव चुनावी चौडा चौतारी चौबीस
छलफल छिटो छिमेकी छुट्टी छुट्टै छेउ
जग्गा जनता जनसंख्या जनजीवन जन्म जन्मदिन जिल्ला जीवन जिन्दगी
ज्यान ज्वरो ज्वालामुखी
झगडा झ्याल झोल
टोल ट्राफिक ट्रेन टिकट टेलिभिजन टेलिफोन
ठगी ठेक्का ठेगाना ठाउँठाउँ
डराउनु डाक्टर डुल्नु
ढोका ढुङ्गा ढुक्क
तल तला तयार तयारी तालिम तारा ताराहरु तितो तिर्नु तीज तीर्थ
तुलना तुलनात्मक तेस्रो त्यसपछि त्यसैले त्यहाँ त्यही त्यहीँ
थकान थप थाले थाल्नु थाहा थियो
दक्षिण दशैं दसैं दशैँ दशैंको दसैंको दशैँको
दर्ता दर्शक दल दलाल दवाब दबाब दश दसौं
दिशा दिवस दिवाली दिदीबहिनी दिमाग दीपावली
दुखः दुःखी दुर्घटना दुर्गम दुर्लभ दूध दूरदराज
देखेर देखि देखिन्छ देखियो देश देशभक्त देशभर
धन धनी धर्म धार्मिक ध्यान धुलो धेरैजसो
नगर नजिक नजिकै नदी नम्बर नयाँ नारा नाटक नाच्नु नाच
नागरिक नागरिकता नाता नातागोता नाफा नाप्नु
निधार नियम नियमित निराश निवेदन निश्चय निश्चित
नीति नैतिक नोकरी नोक्सान
पक्का पक्राउ पछाडि पछाडी पछिल्लो पत्र पत्रकार पत्रिका
पद पदक पदयात्रा पदमार्ग पर्यटक पर्यटन पर्याप्त
परिचय परिवर्तन परिणाम परिक्षा परीक्षा परम्परा परम्परागत
परिषद पर्यावरण पर्यावरणीय परेवा पर्व पर्वतीय
पहाड पहिरो पहेंलो पाउनु पाएँ पाउँछ पाउँछु
पाठ पाठशाला पाठक पिट्नु पिता पितृ
पुरस्कार पुर्याउनु पुग्नु पुगे पुगेँ पुग्यो
पुस्तक पुस्ता पूर्वी पूर्व पूर्वधार
पेसा पेश
प्रकाशित प्रकृति प्राकृतिक प्रगति प्रगतिशील
प्रजातन्त्र प्रजातान्त्रिक प्रणाली प्रमुख
प्रतीक्षा प्रति प्रतिज्ञा प्रतिक्रिया प्रतिवेदन
प्रदेश प्रदेश सरकार प्रधानमन्त्री प्रश्न प्रश्नोत्तर
प्रभाव प्रभावित प्रयोग प्रयोगकर्ता प्रयोजन
प्रहरी प्रहरी चौकी प्रहरी कार्यालय
प्राइभेट प्राइभेट स्कूल
फलफूल फलफूलको फाइदा फैसला फोन फेरबदल
बजार भाडा भाडामा भाडा तिर्नु
बच्चा बच्चाहरु बचपन बचत बचाउनु
बढ्नु बढ्यो बढी बढ्दो
बन वनजंगल वनस्पति
बरसात बर्खा बसपार्क बस स्टप
बाँकी बाँच्नु बाँच्न बाँध
बाटोघाटो बाध्य बाध्यता
बिदा बिदेश विदेश विदेशी बिरामी बिरामीलाई
बिषय विषय विषयवस्तु बिस्तार विस्तार विस्तारै
बुद्धि बुद्धिमत्ता बुवा बाबु
बेला बेलाबेला बेलायत बेलायती
बैंक बैङ्क बैङ्किङ बैङ्कर
बोली बोलीचाली बोल्न बोल्दा
भाग भाषण भित्ता भित्र भित्रिनु
भीड भीडभाड भुइँ भुइँचालो भूकम्प भूमि
भोक भोकाइ भोलिपल्ट भविष्य
मन्दिर मन्त्रालय मन्त्री मजदुर मजदूर
मजा मज्जा मजाक
मताधिकार मतदान मतदाता
महत्त्व महत्त्वपूर्ण महँगो महंगी
महिला महिलालाई
माग माग्नु मागे
माछा माछामासु
माटो माटोको
माध्यम माध्यमबाट
मानव मानवअधिकार मानवीय
मालिक मालसामान
मिल्नु मिलेर मिल्यो
मुख्य मुख्यतः मुख
मुद्दा मुद्दाको
मूल्य मूल्याङ्कन
मृत्यु मृतक
मेहनत मेहनती
मौका मौसम
यन्त्र यन्त्रपात
यदि यद्यपि
योजना योजनाबद्ध
युवा युवती युवक
योद्धा युद्ध
रकम रकमको
रगत रगतको
रचना रचनात्मक
रन रनर
रमाइलो रमाउनु
राजनीति राजनीतिक राजधानी राज्य राष्ट्र राष्ट्रिय
राहत राशि
रुचि रुचिकर
रेल रेल्वे
रोग रोगी
रोकनु रोक्नु रोकियो
लक्ष्य लक्षित
लगानी लगानीकर्ता
लडाईँ लडाइँ लड्नु
लाख लाखौं
लाज लाजमान्नु
लाभ लाभदायक
लिङ्क लिंक
लेख लेखक लेखाइ
लोकतन्त्र लोकतान्त्रिक
लोग्ने लोग्नेमान्छे
वन वनरक्षक
वातावरण वातावरणीय
वास्तव वास्तवमा वास्तविक
विज्ञान वैज्ञानिक
विदेश विदेशमा
विवाह विवाहित
विश्लेषण विश्लेषणात्मक
विश्व विश्वभरि विश्वसनीय विश्वास
विद्यालय विद्यार्थी विद्या
विपद् विपद्‌ व्यवस्थापन
व्यवसाय व्यवसायी व्यवसायिक
व्यवस्था व्यवस्थित
शङ्का शंका
शक्ति शक्तिशाली
शब्द शब्दकोश
शरीर शारीरिक
शिक्षा शिक्षक शिक्षिका शिक्षित
शीर्षक शीर्ष
शान्ति शान्तिपूर्ण
शहर सहरी
शुल्क शुल्कीय
शुरु सुरु सुरुवात सुरुआत
शेयर सेयर
श्रम श्रमिक
श्रद्धा श्रद्धाञ्जली
संकट संकटपूर्ण
संगठन सङ्गठन
संघ संघीय
संसद संसद् संसदीय
संस्कार सांस्कृतिक संस्कृति
संस्था संस्थापक
सडक सडकमार्ग
सफल सफलता सफल हुनु
समझदारी सम्झनु सम्झना
सम्पत्ति सम्पत्तिको
सम्मान सम्मानित
सम्भव सम्भावना सम्भावित
सम्बन्ध सम्बन्धित
सम्मेलन सम्मेलनमा
सरकार सरकारी
सर्त सर्तहरू
सस्तो सस्ता
सहयोग सहयोगी सहयोग गर्नु
सहायता सहायक
सहिद सहिदको
साथीसंग साथसाथै
साधन साधारण
सानोतिनो
सामान सामग्री
सामाजिक सामाजिकता
साहित्य साहित्यिक
सिफारिस सिफारिस गर्नु
सिमाना सीमा
सुरक्षा सुरक्षित
सुविधा सुविधाजनक
सूचना सूची
सेवा सेवक सेविका
सैनिक सेना
सोच्नु सोचेँ सोच्यो
स्थान स्थानीय
स्थिति स्थितिको
स्तर स्तरीय
स्पष्ट स्पष्ट रूपमा
स्वास्थ्य स्वास्थ्यकर्मी
स्वाद स्वादिलो
हजार हजारौं
हप्ता दिन
हत्या हत्यारा
हिमाल हिमाली हिउँ
हुलाक हुलाकी
होटल होटलमा
होसियार होसियारी
""".split()


def nepali_words():
    return unique(NEPALI_PRIORITY + NEPALI_MORE)


ROMAN_EXTRA = """
# extra everyday romanizations
mero ghar	मेरो घर
timro naam	तिम्रो नाम
kasto cha	कस्तो छ
sanchai chu	सञ्चै छु
sanchai chhu	सञ्चै छु
ma pani	म पनि
timi pani	तिमी पनि
tapai pani	तपाईं पनि
kaha chau	कहाँ छौ
kaha cha	कहाँ छ
ke gardai	के गर्दै
ke garchau	के गर्छौ
ke garchha	के गर्छ
khana khayau	खाना खायौ
pani deu	पानी देऊ
chiya kha	चिया खा
sutna janxu	सुत्न जान्छु
sutna janchu	सुत्न जान्छु
padhna	पढ्न
lekhna	लेख्न
bolna	बोल्न
herna	हेर्न
sunna	सुन्न
aauna	आउन
jana	जान
basna	बस्न
garnuparne	गर्नुपर्ने
garnuparcha	गर्नुपर्छ
jana man	जान मन
aauna man	आउन मन
maya garchu	माया गर्छु
maya lagcha	माया लाग्छ
samjhana	सम्झना
samjhinchu	सम्झिन्छु
birse	बिर्सेँ
birsina	बिर्सिनँ
thakyo	थाक्यो
thakai	थाकाइ
nidra	निद्रा
bhok	भोक
bhok lagyo	भोक लाग्यो
tirsha	तिर्खा
tirsha lagyo	तिर्खा लाग्यो
joro	ज्वरो
aukhayo	औखायो
ausadhi	औषधि
ausadhi khanu	औषधि खानु
aspatal janu	अस्पताल जानु
doctor	डाक्टर
daktar	डाक्टर
nurse	नर्स
police	प्रहरी
prahari	प्रहरी
sadak	सडक
bato	बाटो
gaadi	गाडी
gadi	गाडी
motar	मोटर
motor	मोटर
tempo	टेम्पो
micro	माइक्रो
buspark	बसपार्क
ticket	टिकट
paisa chaina	पैसा छैन
mahango	महँगो
sasto	सस्तो
thik cha	ठीक छ
huncha ni	हुन्छ नि
pardaina ni	पर्दैन नि
bujhena	बुझेन
bujhyo	बुझ्यो
thaaha	थाहा
thaha cha	थाहा छ
thaha chaina	थाहा छैन
wasto	वास्तव
vastav	वास्तव
sakcha	सक्छ
sakdaina	सक्दैन
milena	मिलेन
bhayena	भएन
chhaina	छैन
chaina	छैन
chhainau	छैनौ
hunna	हुन्न
nabhana	नभन
nagarnu	नगर्नु
najau	नजाऊ
naau	नआऊ
bas	बस्
aau	आऊ
her	हेर
sun	सुन
bol	बोल
kha	खा
deu	देऊ
leu	लेऊ
rakha	राख
lyau ta	ल्याऊ त
hida	हिँड
hidnu	हिँड्नु
daudinu	दौडिनु
nachnu	नाच्नु
hasnu	हाँस्नु
runu	रुनु
roye	रोएँ
hase	हाँसेँ
risaae	रिसाएँ
daraayo	डरायो
dar	डर
himmat	हिम्मत
sahas	साहस
mehnat	मेहनत
kosis	कोसिस
koshish	कोसिस
safal	सफल
asafal	असफल
jit	जित
haar	हार
khel	खेल
khelnu	खेल्नु
git	गीत
gana	गाना
bajha	बाजा
nach	नाच
cinema	सिनेमा
chalchitra	चलचित्र
serial	सिरियल
news	न्यूज
radio	रेडियो
tv	टिभी
telephone	टेलिफोन
sms	एसएमएस
whatsapp	ह्वाट्सएप
insta	इन्स्टा
instagram	इन्स्टाग्राम
facebookma	फेसबुकमा
story	स्टोरी
reel	रिल
live	लाइभ
call gara	कल गर
message pathau	मेसेज पठाऊ
photo kich	फोटो खिच
video banaau	भिडियो बनाऊ
net chaina	नेट छैन
charge chaina	चार्ज छैन
battery	ब्याट्री
password	पासवर्ड
login	लगइन
download	डाउनलोड
upload	अपलोड
file	फाइल
folder	फोल्डर
link pathau	लिंक पठाऊ
google gara	गुगल गर
map	म्याप
location	लोकेसन
address	ठेगाना
thegana	ठेगाना
number	नम्बर
phone number	फोन नम्बर
ghar ko	घरको
office ko	अफिसको
school ma	स्कूलमा
college ma	कलेजमा
class ma	क्लासमा
padhai	पढाइ
homework	होमवर्क
exam	एग्जाम
pariksha	परीक्षा
result	रिजल्ट
pass	पास
fail	फेल
teacher	टिचर
sir	सर
miss	मिस
student	स्टुडेन्ट
sathi haru	साथीहरू
maya ko	मायाको
bihe	बिहे
byaha	ब्याह
logne	लोग्ने
swasni	स्वास्नी
chhoro	छोरो
babu	बाबु
ama	आमा
hajurba	हजुरबुबा
hajurama	हजुरआमा
kaka	काका
kaki	काकी
mama	मामा
maiya	माइजू
phupu	फुपू
nani	नानी
babu	बाबु
dai ho	दाइ हो
didi ho	दिदी हो
bhai ho	भाइ हो
bahini ho	बहिनी हो
khusi chu	खुसी छु
dukhi chu	दुःखी छु
ris uthyo	रिस उठ्यो
manna parcha	मान्नु पर्छ
mannu	मान्नु
biswas	विश्वास
biswas cha	विश्वास छ
sandeha	सन्देह
dar lagyo	डर लाग्यो
maja aayo	मजा आयो
ramailo cha	रमाइलो छ
boring cha	बोरिङ छ
busy chu	बिजी छु
free chu	फ्री छु
time chaina	टाइम छैन
bhetaunla	भेटौँला
bolula	बोलुँला
herula	हेरुँला
auchhu	आउँछु
janchhu	जान्छु
baschhu	बस्छु
garchhu	गर्छु
khanchhu	खान्छु
padhchhu	पढ्छु
lekhchhu	लेख्छु
herchhu	हेर्छु
sunchhu	सुन्छु
bolchhu	बोल्छु
sikchhu	सिक्छु
bujhchhu	बुझ्छु
sodhchhu	सोध्छु
dinchhu	दिन्छु
linchhu	लिन्छु
rakhchhu	राख्छु
pathauchhu	पठाउँछु
bolauchhu	बोलाउँछु
nachhu	नाच्छु
haschhu	हाँस्छु
rochhu	रोन्छु
sutchhu	सुत्छु
uthchhu	उठ्छु
hidchhu	हिँड्छु
daudinchhu	दौडिन्छु
khelchhu	खेल्छु
nachchhu	नाच्छु
gauchhu	गाउँछु
banachhu	बनाउँछु
kinchhu	किन्छु
bechchhu	बेच्छु
tirchhu	तिर्छु
magchhu	माग्छु
dinxu	दिन्छु
linxu	लिन्छु
garxu	गर्छु
janxu	जान्छु
aauxu	आउँछु
khana banaau	खाना बनाऊ
khana lyau	खाना ल्याऊ
pani lyau	पानी ल्याऊ
chiya banaau	चिया बनाऊ
momo khane	मोमो खाने
chowmein	चाउमिन
chowmin	चाउमिन
thukpa	थुक्पा
selroti	सेलरोटी
gundruk	गुन्द्रुक
achar	अचार
dahi	दही
mahi	मोही
dudh	दूध
chij	चिज
puri	पुरी
roti tarkari	रोटी तरकारी
dal bhat	दाल भात
masu	मासु
kukhura	कुखुरा
khasiko	खसीको
machha	माछा
anda	अण्डा
phalphul	फलफूल
syau	स्याउ
kera	केरा
suntala	सुन्तला
aap	आँप
angur	अंगुर
tarul	तरुल
aalu	आलु
pyaj	प्याज
lasun	लसुन
aduwa	अदुवा
khursani	खुर्सानी
nunn	नुन
chinni	चिनी
tel	तेल
gheu	घिउ
bhat masino	भात मसिनो
mitho cha	मिठो छ
naramro cha	नराम्रो छ
piro cha	पिरो छ
guliyo	गुलियो
amilo	अमिलो
tito	तितो
chiso pani	चिसो पानी
tato pani	तातो पानी
raksi	रक्सी
jaad	जाँड
churot	चुरोट
piunu hudaina	पिउनु हुँदैन
dashain	दशैं
tihar	तिहार
teej	तीज
holi	होली
chhath	छठ
losar	ल्होसार
buddha jayanti	बुद्ध जयन्ती
republic day	गणतन्त्र दिवस
new year	नयाँ वर्ष
birthday	जन्मदिन
janmadin	जन्मदिन
shubhakamana	शुभकामना
badhai cha	बधाई छ
dhanyabad cha	धन्यवाद छ
maaf garnu	माफ गर्नुहोस्
kripaya garnu	कृपया गर्नुहोस्
swagat cha	स्वागत छ
bhetam	भेटम
pheribhetaula	फेरि भेटौँला
bholi kurau	भोलि कुराऔँ
aja nai	आज नै
ahile nai	अहिले नै
pachi garni	पछि गर्नी
bholi garni	भोलि गर्नी
hijo gareko	हिजो गरेको
agadi	अगाडि
pachadi	पछाडि
mathi	माथि
tala	तल
bhitra	भित्र
bahira	बाहिर
najik	नजिक
tala tira	तलतिर
mathi tira	माथितिर
daaya	दायाँ
baya	बायाँ
sidha	सिधा
ulta	उल्टो
dherai ramro	धेरै राम्रो
ali ramro	अलि राम्रो
ekdam ramro	एकदम राम्रो
sabaibhanda	सबैभन्दा
pahilo	पहिलो
dosro	दोस्रो
tesro	तेस्रो
antim	अन्तिम
suru	सुरु
antya	अन्त्य
bich	बिच
madhya	मध्य
 slc
see	एसईई
plus two	प्लस टू
bachelor	ब्याचलर
master	मास्टर
job cha	जागिर छ
jagire	जागिरे
talab	तलब
salary	तलब
kaam garni	काम गर्नी
kaam chaina	काम छैन
byapar	व्यापार
pasal kholne	पसल खोल्ने
sastoma	सस्तोमा
mahangoma	महँगोमा
discount	डिस्काउन्ट
offer	अफर
bill	बिल
receipt	रसिद
hisab	हिसाब
udhar	उधारो
rin	ऋण
byaj	ब्याज
bank	बैंक
account	खाता
paisa pathau	पैसा पठाऊ
esewa	इसेवा
khalti	खल्ती
ime pay	आईएमई पे
connectips	कनेक्टआईपीएस
"""


def parse_roman_block(text):
    entries = []
    for line in text.splitlines():
        raw = line.strip()
        if not raw or raw.startswith("#") or "\t" not in raw:
            continue
        key, value = raw.split("\t", 1)
        key = key.strip()
        value = value.strip()
        if key and value:
            entries.append((key, value))
    return entries


def expand_roman():
    existing = (RAW / "roman_nepali_dictionary.tsv").read_text(encoding="utf-8")
    extra = parse_roman_block(ROMAN_EXTRA)
    seen = set()
    lines = []
    for line in existing.splitlines():
        lines.append(line)
        stripped = line.strip()
        if stripped and not stripped.startswith("#") and "\t" in stripped:
            seen.add(stripped.split("\t", 1)[0].strip().lower())
    added = 0
    if lines and lines[-1] != "":
        lines.append("")
    lines.append("# Expanded everyday Romanized Nepali")
    for key, value in extra:
        if key.lower() in seen:
            continue
        # skip accidental spaces-only keys and invalid rows
        if " " in key:
            # phrase mappings are useful but current converter is word-based;
            # keep single tokens only so lookup and tests stay word-oriented.
            continue
        seen.add(key.lower())
        lines.append(f"{key}\t{value}")
        added += 1
    (RAW / "roman_nepali_dictionary.tsv").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return added, len(seen)


def main():
    RAW.mkdir(parents=True, exist_ok=True)
    english = english_words()
    nepali = nepali_words()
    n_en = write_list(
        RAW / "english_vocabulary.txt",
        "# Frequency-ordered offline English vocabulary. Earlier lines rank higher.",
        english,
    )
    n_ne = write_list(
        RAW / "nepali_vocabulary.txt",
        "# Frequency-ordered offline Nepali Devanagari vocabulary. Earlier lines rank higher.",
        nepali,
    )
    added, total_roman = expand_roman()
    print(f"english={n_en} nepali={n_ne} roman_keys={total_roman} roman_added={added}")


if __name__ == "__main__":
    main()
