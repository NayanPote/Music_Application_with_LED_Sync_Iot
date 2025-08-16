package com.nayanpote.Nayora;

import com.nayanpote.musicalledsbynayan.R;

import java.util.ArrayList;
import java.util.List;

public class playlistdata {

    public static List<Song> getDefaultPlaylist() {
        List<Song> playlist = new ArrayList<>();

        playlist.add(new Song("Roar of Narasimha", "Sam C.S", R.raw.roar_of_narsimha, R.drawable.narasimha_cover));
        playlist.add(new Song("Jai Shri Ram", "Ajay-Atul", R.raw.jai_shri_ram, R.drawable.jai_shri_ram_cover));
        playlist.add(new Song("Big Dawgs", "Hanuman Kind", R.raw.big_dawgs, R.drawable.big_dawgs_cover));
        playlist.add(new Song("Sapphire", "Ed Sheeran", R.raw.sapphire, R.drawable.sapphire_cover));
        playlist.add(new Song("Millionaire", "Yo Yo Honey Singh", R.raw.millionaire, R.drawable.millionaire_cover));
        playlist.add(new Song("Run It Up", "Hanuman Kind", R.raw.runitup, R.drawable.run_it_up_cover));
        playlist.add(new Song("Six Days", "DJ Shadow", R.raw.six_days, R.drawable.six_days_cover));
        playlist.add(new Song("Tokyo Drift", "Teriyaki Boyz", R.raw.tokyo_drift, R.drawable.tokyo_drift_cover));
        playlist.add(new Song("Skyfall", "Adele", R.raw.skyfall, R.drawable.skyfall_cover));
        playlist.add(new Song("Powerhouse", "Anirudh Ravichander", R.raw.powerhouse, R.drawable.powerhouse_cover));
        playlist.add(new Song("Mr. Rambo", "Yung Sammy", R.raw.mr_rambo, R.drawable.mrrambo_cover));
        playlist.add(new Song("Kurchi Madathapetti", "Sri Krishna, Sahithi Chaganti", R.raw.kurchi_madathapetti, R.drawable.kurchi_madathapetti_cover));
        playlist.add(new Song("Tokyo Bon (Makudonarudo)", "Namewee, Meu Ninomiya", R.raw.tokyobon_makudorarudo, R.drawable.tokyobon_cover));
        playlist.add(new Song("Slumber Party", "Ashnikko", R.raw.slumber_party, R.drawable.slumber_party_cover));
        playlist.add(new Song("Hanuman Chalisa", "Shankar Mahadevan", R.raw.hanuman_chalisa, R.drawable.hanuman_cover));
        playlist.add(new Song("Happy Nation", "Ace Of Base", R.raw.happy_nation, R.drawable.happy_nation_cover));
        playlist.add(new Song("Aaya Re Toofan", "Vaishali Samant, A.R.Rahman", R.raw.aaya_re_toofan, R.drawable.aaya_re_toofan));
        playlist.add(new Song("Arjan Vailly", "Bhupinder Babbal", R.raw.arjan_vailly, R.drawable.arjan_vailly_cover));
        playlist.add(new Song("Dolby Walya", "Nagesh Morwekar, Ajay-Atul", R.raw.dolby_walya, R.drawable.dolby_walya_cover));
        playlist.add(new Song("Shwasat Raja Dhyasat Raja", "Avdhoot Gandhi, Devdutta Manisha Baji", R.raw.shwasat_raja_dhyasat_raja, R.drawable.shwasat_raja_dhyasat_raja_cover));
        playlist.add(new Song("Aigiri Nandini", "Brodha V", R.raw.aigiri_nandini, R.drawable.aigiri_nandini_cover));
        playlist.add(new Song("Raja Aala", "Avdhoot Gandhi, Devdutta Manisha Baji", R.raw.raja_aala, R.drawable.raja_aala_cover));
        playlist.add(new Song("Tumbbad", "Ajay-Atul", R.raw.tumbbad, R.drawable.tumbbad_cover));

        return playlist;
    }
}
