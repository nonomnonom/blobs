# Appendix — records excluded from `wikisource-en` edition `2026-09.3` by the runtime text contract

Generated 2026-10-24 from the reviewed edition's own `rejected` streams (`corpus/artifacts/records/wikisource-en-all-nopic-2026-09.3/*.rejected.jsonl.gz`).
Count: **135**. Every one carries the reason `Invalid body: contains a Unicode
replacement character`, raised by `blobs_corpus.records.validate_document`, which now applies the
same no-U+FFFD rule to `documents.body` that Android's `LibraryValidator` applies to every column.

These records were rejected at extraction, so they never received a document id and belong to no
partition volume. The replacement character occurs in the transcribed body text only: none of the
135 titles or archive entry paths contains U+FFFD (measured over the `rejected` streams), so both
columns are reproduced verbatim below.

| # | Archive index | Archive entry path | Title |
| --- | ---: | --- | --- |
| 1 | 46,505 | `2024_Elkhorn–Bennington–Blair_Tornado_Finalized_Report` | 2024 Elkhorn–Bennington–Blair Tornado Finalized Report |
| 2 | 65,282 | `A_Dictionary_of_Music_and_Musicians/Beethoven,_Ludwig_van,_catalogue` | A Dictionary of Music and Musicians/Beethoven, Ludwig van, catalogue |
| 3 | 69,416 | `A_Dictionary_of_Music_and_Musicians/Tonic_Sol-fa` | A Dictionary of Music and Musicians/Tonic Sol-fa |
| 4 | 75,239 | `A_History_of_Chinese_Literature/Book_1/Chapter_5` | A History of Chinese Literature/Book 1/Chapter 5 |
| 5 | 75,240 | `A_History_of_Chinese_Literature/Book_1/Chapter_6` | A History of Chinese Literature/Book 1/Chapter 6 |
| 6 | 75,241 | `A_History_of_Chinese_Literature/Book_4/Chapter_1` | A History of Chinese Literature/Book 4/Chapter 1 |
| 7 | 75,249 | `A_History_of_Chinese_Literature/Index` | A History of Chinese Literature/Index |
| 8 | 84,672 | `A_Review_of_Allegations_of_a_Double_Standard_of_Discipline_at_the_FBI` | A Review of Allegations of a Double Standard of Discipline at the FBI |
| 9 | 84,818 | `A_Room_with_a_View/Adverts` | A Room with a View/Adverts |
| 10 | 89,549 | `A_Treatise_on_Electricity_and_Magnetism/Index` | A Treatise on Electricity and Magnetism/Index |
| 11 | 89,605 | `A_Treatise_on_Electricity_and_Magnetism/Part_IV/Chapter_XVIII` | A Treatise on Electricity and Magnetism/Part IV/Chapter XVIII |
| 12 | 89,607 | `A_Treatise_on_Electricity_and_Magnetism/Part_IV/Chapter_XXI` | A Treatise on Electricity and Magnetism/Part IV/Chapter XXI |
| 13 | 89,608 | `A_Treatise_on_Electricity_and_Magnetism/Part_IV/Chapter_XXII` | A Treatise on Electricity and Magnetism/Part IV/Chapter XXII |
| 14 | 89,610 | `A_Treatise_on_Electricity_and_Magnetism/Plates` | A Treatise on Electricity and Magnetism/Plates |
| 15 | 170,261 | `American_ambiguities_in_dealing_with_terror_-_4_January_2009` | American ambiguities in dealing with terror - 4 January 2009 |
| 16 | 170,963 | `An_Annotated_Bibliography_of_the_Apollo_Program/General_Works` | An Annotated Bibliography of the Apollo Program/General Works |
| 17 | 170,964 | `An_Annotated_Bibliography_of_the_Apollo_Program/Juvenile_Literature` | An Annotated Bibliography of the Apollo Program/Juvenile Literature |
| 18 | 170,965 | `An_Annotated_Bibliography_of_the_Apollo_Program/Operations` | An Annotated Bibliography of the Apollo Program/Operations |
| 19 | 170,970 | `An_Annotated_Bibliography_of_the_Apollo_Program/The_Space_Race` | An Annotated Bibliography of the Apollo Program/The Space Race |
| 20 | 184,234 | `Anatomy_of_a_Murder_-_14_January_2009` | Anatomy of a Murder - 14 January 2009 |
| 21 | 274,188 | `Basel_Convention` | Basel Convention |
| 22 | 277,064 | `Beryllium_in_Air_by_Field-Portable_Fluorometry_(7704)` | Beryllium in Air by Field-Portable Fluorometry (7704) |
| 23 | 277,065 | `Beryllium_in_Surface_Wipes_by_Field-Portable_Fluorometry_(9110)` | Beryllium in Surface Wipes by Field-Portable Fluorometry (9110) |
| 24 | 285,201 | `Brahmajāla_Sutta` | Brahmajāla Sutta |
| 25 | 304,426 | `Catholic_Encyclopedia_(1913)/Pope_Innocent_I` | Catholic Encyclopedia (1913)/Pope Innocent I |
| 26 | 304,430 | `Catholic_Encyclopedia_(1913)/Pope_Innocent_IX` | Catholic Encyclopedia (1913)/Pope Innocent IX |
| 27 | 304,431 | `Catholic_Encyclopedia_(1913)/Pope_Innocent_VI` | Catholic Encyclopedia (1913)/Pope Innocent VI |
| 28 | 304,433 | `Catholic_Encyclopedia_(1913)/Pope_Innocent_VIII` | Catholic Encyclopedia (1913)/Pope Innocent VIII |
| 29 | 305,101 | `Catholic_Encyclopedia_(1913)/Rocamadour` | Catholic Encyclopedia (1913)/Rocamadour |
| 30 | 305,828 | `Catholic_Encyclopedia_(1913)/St._Anselm_(1)` | Catholic Encyclopedia (1913)/St. Anselm (1) |
| 31 | 308,667 | `Cats_and_Dogs` | Cats and Dogs |
| 32 | 309,973 | `Chandler-Lake_Wilson_Minnesota_F5_Tornado_of_June_16,_1992:_Revisited_on_the_10th_Anniversary` | Chandler-Lake Wilson Minnesota F5 Tornado of June 16, 1992: Revisited on the 10th Anniversary |
| 33 | 312,124 | `China_Djibouti_Communique_Establishing_Diplomatic_Relations` | China Djibouti Communique Establishing Diplomatic Relations |
| 34 | 312,835 | `Christian_Non-Resistance/Chapter_2` | Christian Non-Resistance/Chapter 2 |
| 35 | 315,658 | `Coin's_Financial_School/Appendix` | Coin's Financial School/Appendix |
| 36 | 315,665 | `Coin's_Financial_School/Index` | Coin's Financial School/Index |
| 37 | 327,960 | `Curtis's_Botanical_Magazine/Volume_63/3500` | Curtis's Botanical Magazine/Volume 63/3500 |
| 38 | 371,586 | `Digital_Signature_Standard_(DSS)` | Digital Signature Standard (DSS) |
| 39 | 372,134 | `Distinguished_Churchmen_and_Phases_of_Church_Work/Chapter_6` | Distinguished Churchmen and Phases of Church Work/Chapter 6 |
| 40 | 382,016 | `Elementary_Lessons_In_The_Swatow_Dialect/Vocabulary` | Elementary Lessons In The Swatow Dialect/Vocabulary |
| 41 | 399,163 | `Energy_Policy_Act_of_2005/Title_I` | Energy Policy Act of 2005/Title I |
| 42 | 409,314 | `Fact_sheet:_Former_GTMO_Detainee_Terrorism_Trends` | Fact sheet: Former GTMO Detainee Terrorism Trends |
| 43 | 413,904 | `Fields,_Factories_and_Workshops/Chapter_V:_The_Possibilities_of_Agriculture,_Part_3` | Fields, Factories and Workshops/Chapter V: The Possibilities of Agriculture, Part 3 |
| 44 | 419,238 | `Fouad_Mahmoud_Al_Rabiah_v._USA_(2009-09-26)` | Fouad Mahmoud Al Rabiah v. USA (2009-09-26) |
| 45 | 431,098 | `HKSAR_v_Ng_Gordon_Ching-hang_and_others/Reasons_for_Verdict` | HKSAR v Ng Gordon Ching-hang and others/Reasons for Verdict |
| 46 | 434,018 | `Harper_&_Row_v._Nation_Enterprises/Opinion_of_the_Court` | Harper & Row v. Nation Enterprises/Opinion of the Court |
| 47 | 441,062 | `History_of_Oregon_Literature/Chapter_9` | History of Oregon Literature/Chapter 9 |
| 48 | 444,955 | `How_the_Other_Half_Lives/Chapter_V` | How the Other Half Lives/Chapter V |
| 49 | 444,957 | `How_the_Other_Half_Lives/Chapter_VII` | How the Other Half Lives/Chapter VII |
| 50 | 444,961 | `How_the_Other_Half_Lives/Chapter_XII` | How the Other Half Lives/Chapter XII |
| 51 | 450,496 | `In_defense_of_Harriet_Shelley,_and_other_essays/The_German_Chicago` | In defense of Harriet Shelley, and other essays/The German Chicago |
| 52 | 453,118 | `International_Religious_Freedom_Report_2004_-_Sweden` | International Religious Freedom Report 2004 - Sweden |
| 53 | 455,863 | `Janey_and_the_Social_Revolution` | Janey and the Social Revolution |
| 54 | 457,485 | `Jimmy_Carter's_Fourth_State_of_the_Union_Address` | Jimmy Carter's Fourth State of the Union Address |
| 55 | 458,221 | `John_Kerry's_testimony_before_the_Senate_Foreign_Relations_Committee` | John Kerry's testimony before the Senate Foreign Relations Committee |
| 56 | 462,382 | `Kassapa_Sihanada_Sutta` | Kassapa Sihanada Sutta |
| 57 | 468,860 | `Law_of_Administration_for_the_State_of_Iraq_for_the_Transitional_Period` | Law of Administration for the State of Iraq for the Transitional Period |
| 58 | 469,244 | `Layered_Architecture_for_Quantum_Computing` | Layered Architecture for Quantum Computing |
| 59 | 473,402 | `Letters_from_Alexander_Henry_Haliday_to_Hermann_Loew,_1867` | Letters from Alexander Henry Haliday to Hermann Loew, 1867 |
| 60 | 473,404 | `Letters_from_Alexander_Henry_Haliday_to_Hermann_Loew_25_May_1862,_September_6_1862,_1_November_1862` | Letters from Alexander Henry Haliday to Hermann Loew 25 May 1862, September 6 1862, 1 November 1862 |
| 61 | 482,650 | `Love_and_Pain/4` | Love and Pain/4 |
| 62 | 483,636 | `Lyra_Heroica` | Lyra Heroica |
| 63 | 483,638 | `Lyra_Heroica/Agincourt` | Lyra Heroica/Agincourt |
| 64 | 485,272 | `Mahali_Sutta` | Mahali Sutta |
| 65 | 509,216 | `Munsey's_Magazine/Angelica/Part_1` | Munsey's Magazine/Angelica/Part 1 |
| 66 | 509,217 | `Munsey's_Magazine/Angelica/Part_2` | Munsey's Magazine/Angelica/Part 2 |
| 67 | 510,329 | `My_Favourite_Murder` | My Favourite Murder |
| 68 | 511,407 | `Mysticism:_A_Study_in_Nature_and_Development_of_Spiritual_Consciousness/Part_1/Chapter_IV` | Mysticism: A Study in Nature and Development of Spiritual Consciousness/Part 1/Chapter IV |
| 69 | 512,664 | `NOAA_Storm_Events_Database_–_2021_Western_Kentucky_tornado/Obion_County` | NOAA Storm Events Database – 2021 Western Kentucky tornado/Obion County |
| 70 | 512,723 | `NTSB_accident_summary_for_N130HP` | NTSB accident summary for N130HP |
| 71 | 512,725 | `NTSB_accident_summary_for_N7620C` | NTSB accident summary for N7620C |
| 72 | 532,677 | `Notes_by_the_Way/Chapter_3` | Notes by the Way/Chapter 3 |
| 73 | 535,590 | `Official_Code_of_Georgia_Annotated/Title_1/Chapter_1/Section_1` | Official Code of Georgia Annotated/Title 1/Chapter 1/Section 1 |
| 74 | 541,889 | `One_Hundred_and_Three` | One Hundred and Three |
| 75 | 542,767 | `Oregon_Geographic_Names_(1952)/R` | Oregon Geographic Names (1952)/R |
| 76 | 542,768 | `Oregon_Geographic_Names_(1952)/S` | Oregon Geographic Names (1952)/S |
| 77 | 546,022 | `Oxford_Book_of_English_Verse_1250-1918` | Oxford Book of English Verse 1250-1918 |
| 78 | 546,046 | `Oxford_Book_of_English_Verse_1250-1918/The_Fish,_the_Man,_and_the_Spirit` | Oxford Book of English Verse 1250-1918/The Fish, the Man, and the Spirit |
| 79 | 546,271 | `PM_Benjamin_Netanyahu's_speech_at_the_79th_UN_General_Assembly` | PM Benjamin Netanyahu's speech at the 79th UN General Assembly |
| 80 | 548,131 | `Passages_from_the_Life_of_a_Philosopher/Adverts` | Passages from the Life of a Philosopher/Adverts |
| 81 | 551,125 | `Personal_Narrative_of_a_Pilgrimage_to_Al_Madinah_and_Meccah/Appendices` | Personal Narrative of a Pilgrimage to Al Madinah and Meccah/Appendices |
| 82 | 551,126 | `Personal_Narrative_of_a_Pilgrimage_to_Al_Madinah_and_Meccah/Volume_I` | Personal Narrative of a Pilgrimage to Al Madinah and Meccah/Volume I |
| 83 | 551,127 | `Personal_Narrative_of_a_Pilgrimage_to_Al_Madinah_and_Meccah/Volume_II` | Personal Narrative of a Pilgrimage to Al Madinah and Meccah/Volume II |
| 84 | 551,128 | `Personal_Narrative_of_a_Pilgrimage_to_Al_Madinah_and_Meccah/Volume_III` | Personal Narrative of a Pilgrimage to Al Madinah and Meccah/Volume III |
| 85 | 551,131 | `Personal_Narrative_of_a_Pilgrimage_to_Al_Madinah_and_Meccah/prefaceMem` | Personal Narrative of a Pilgrimage to Al Madinah and Meccah/prefaceMem |
| 86 | 574,723 | `Poet_Lore/Volume_35/Number_3/The_Great_Freeholder` | Poet Lore/Volume 35/Number 3/The Great Freeholder |
| 87 | 578,921 | `Popular_Science_Monthly/Volume_30/December_1886/Sketch_of_Francois_Arago` | Popular Science Monthly/Volume 30/December 1886/Sketch of Francois Arago |
| 88 | 580,035 | `Popular_Science_Monthly/Volume_38/March_1891/Supposed_Tendencies_to_Socialism` | Popular Science Monthly/Volume 38/March 1891/Supposed Tendencies to Socialism |
| 89 | 580,783 | `Popular_Science_Monthly/Volume_43/June_1893/An_Ethnological_Study_of_the_Yuruks` | Popular Science Monthly/Volume 43/June 1893/An Ethnological Study of the Yuruks |
| 90 | 606,195 | `Reason_(Coleridge)` | Reason (Coleridge) |
| 91 | 610,435 | `Revolution_and_Other_Essays/Fomá_Gordyéeff` | Revolution and Other Essays/Fomá Gordyéeff |
| 92 | 610,440 | `Revolution_and_Other_Essays/The_Golden_Poppy` | Revolution and Other Essays/The Golden Poppy |
| 93 | 622,544 | `Scientific_Papers_of_Josiah_Willard_Gibbs,_Volume_1/Chapter_IIIa` | Scientific Papers of Josiah Willard Gibbs, Volume 1/Chapter IIIa |
| 94 | 626,194 | `Shakespeare's_Plays/Romeo_and_Juliet` | Shakespeare's Plays/Romeo and Juliet |
| 95 | 637,144 | `Speeches_and_Writings_of_M._K._Gandhi/The_Beginning_of_the_Struggle` | Speeches and Writings of M. K. Gandhi/The Beginning of the Struggle |
| 96 | 648,969 | `Terræ-filius:_or,_the_Secret_History_of_the_University_of_Oxford/Terræ_Filius_No._V` | Terræ-filius: or, the Secret History of the University of Oxford/Terræ Filius No. V |
| 97 | 651,108 | `The_Altar_of_the_Dead_(unsourced_edition)/Preface` | The Altar of the Dead (unsourced edition)/Preface |
| 98 | 674,428 | `The_Complete_Poetical_Works_of_John_Milton/The_Life_of_Milton` | The Complete Poetical Works of John Milton/The Life of Milton |
| 99 | 679,323 | `The_Custom_of_the_Country/Chapter_V` | The Custom of the Country/Chapter V |
| 100 | 679,329 | `The_Custom_of_the_Country/Chapter_XII` | The Custom of the Country/Chapter XII |
| 101 | 679,330 | `The_Custom_of_the_Country/Chapter_XIII` | The Custom of the Country/Chapter XIII |
| 102 | 679,332 | `The_Custom_of_the_Country/Chapter_XIX` | The Custom of the Country/Chapter XIX |
| 103 | 679,333 | `The_Custom_of_the_Country/Chapter_XL` | The Custom of the Country/Chapter XL |
| 104 | 679,344 | `The_Custom_of_the_Country/Chapter_XX` | The Custom of the Country/Chapter XX |
| 105 | 679,349 | `The_Custom_of_the_Country/Chapter_XXIX` | The Custom of the Country/Chapter XXIX |
| 106 | 679,355 | `The_Custom_of_the_Country/Chapter_XXXI` | The Custom of the Country/Chapter XXXI |
| 107 | 679,363 | `The_Custom_of_the_Country/Chapter_XXXVIII` | The Custom of the Country/Chapter XXXVIII |
| 108 | 686,364 | `The_Economic_Journal/Volume_1/The_Regulation_of_Railway_Rates` | The Economic Journal/Volume 1/The Regulation of Railway Rates |
| 109 | 699,108 | `The_Ghost_Pirates/Chapter_VIII` | The Ghost Pirates/Chapter VIII |
| 110 | 707,797 | `The_Hunterian_Oration_for_1839` | The Hunterian Oration for 1839 |
| 111 | 723,640 | `The_Maiden_Tribute_of_Modern_Babylon` | The Maiden Tribute of Modern Babylon |
| 112 | 726,442 | `The_Methodist_Hymn-Book_Illustrated/Original_Sources_of_the_Wesley_Hymns` | The Methodist Hymn-Book Illustrated/Original Sources of the Wesley Hymns |
| 113 | 760,775 | `The_Quest_of_the_Historical_Jesus/10` | The Quest of the Historical Jesus/10 |
| 114 | 760,776 | `The_Quest_of_the_Historical_Jesus/11` | The Quest of the Historical Jesus/11 |
| 115 | 760,777 | `The_Quest_of_the_Historical_Jesus/12` | The Quest of the Historical Jesus/12 |
| 116 | 760,778 | `The_Quest_of_the_Historical_Jesus/13` | The Quest of the Historical Jesus/13 |
| 117 | 760,780 | `The_Quest_of_the_Historical_Jesus/15` | The Quest of the Historical Jesus/15 |
| 118 | 760,781 | `The_Quest_of_the_Historical_Jesus/16` | The Quest of the Historical Jesus/16 |
| 119 | 760,791 | `The_Quest_of_the_Historical_Jesus/7` | The Quest of the Historical Jesus/7 |
| 120 | 760,792 | `The_Quest_of_the_Historical_Jesus/8` | The Quest of the Historical Jesus/8 |
| 121 | 760,793 | `The_Quest_of_the_Historical_Jesus/9` | The Quest of the Historical Jesus/9 |
| 122 | 766,186 | `The_Royal_Navy,_a_History_from_the_Earliest_Times_to_the_Present/Volume_1/Chapter_13` | The Royal Navy, a History from the Earliest Times to the Present/Volume 1/Chapter 13 |
| 123 | 766,797 | `The_Sage_and_the_Housewife` | The Sage and the Housewife |
| 124 | 767,687 | `The_Scientific_Monthly/Volume_3` | The Scientific Monthly/Volume 3 |
| 125 | 777,156 | `The_Surprising_Adventures_of_Baron_Munchausen/Introduction` | The Surprising Adventures of Baron Munchausen/Introduction |
| 126 | 788,265 | `The_Women_of_the_Caesars/Chapter_2` | The Women of the Caesars/Chapter 2 |
| 127 | 788,990 | `The_Works_of_Dionysius_the_Areopagite/Ecclesiastical_Hierarchy/Caput_III` | The Works of Dionysius the Areopagite/Ecclesiastical Hierarchy/Caput III |
| 128 | 800,746 | `Thirty-five_Years_of_Luther_Research/Notes` | Thirty-five Years of Luther Research/Notes |
| 129 | 800,747 | `Thirty-five_Years_of_Luther_Research/Researches_on_Particular_Phases_of_the_Life_and_Theology_of_Luther` | Thirty-five Years of Luther Research/Researches on Particular Phases of the Life and Theology of Luther |
| 130 | 804,974 | `Three_arrested_in_ongoing_terror_investigation_(2009-09-20)` | Three arrested in ongoing terror investigation (2009-09-20) |
| 131 | 808,768 | `Tom_Swift_and_His_Giant_Telescope/8` | Tom Swift and His Giant Telescope/8 |
| 132 | 812,688 | `Translation:Likutei_Tefilot` | Translation:Likutei Tefilot |
| 133 | 825,875 | `United_States_Code/Title_42/Chapter_82/Section_6921` | United States Code/Title 42/Chapter 82/Section 6921 |
| 134 | 832,683 | `United_States_v._Marshall/Opinion_of_the_Court` | United States v. Marshall/Opinion of the Court |
| 135 | 836,762 | `Voyage_of_the_Liberdade` | Voyage of the Liberdade |

## Reconciliation

The predecessor edition `2026-09.2` carried 622,149 documents and this edition carries 622,014:
a difference of exactly 135, and the rejection histogram changes by `+135 Invalid body: contains a Unicode replacement character` and by nothing else.
The predecessor's own rights review had already counted 135 bodies containing U+FFFD, so the
exclusion set is the set that review named but shipped anyway.

Attribution census after exclusion, measured by full iteration of all 622,014 retained records
(`corpus/work/scan-fffd-2026-10-24/census-wikisource-3.txt`): 622,014 with a `history_url`
(100%), 619,117 with a MediaWiki `revision_id`, 2,897 without, 0 records failing
`validate_document`, and 0 retained bodies containing U+FFFD. The 2,897 records without a revision
id are unchanged from the predecessor, so all 135 exclusions had revision ids.
