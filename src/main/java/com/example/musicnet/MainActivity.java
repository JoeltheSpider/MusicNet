package com.example.musicnet;


import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.MediaStore;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.storage.FirebaseStorage;

import android.widget.Button;
import android.widget.ListView;
import android.os.IBinder;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import com.example.musicnet.MusicService.MusicBinder;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;

import android.view.MenuItem;
import android.view.View;
import android.widget.MediaController.MediaPlayerControl;

import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements MediaPlayerControl{
    FirebaseStorage firebaseStorage = FirebaseStorage.getInstance();
    DatabaseReference databaseReference;
    private static final int permission=1;
    private ArrayList<Song> songList;
    private ArrayList<Song> songListFire;
    private MusicService musicSrv;
    private Intent playIntent;
    private boolean local=true;
    private boolean musicBound=false;
    private MusicController controller;
    private boolean paused=false, playbackPaused=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Checking permissions
        if(ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            if(ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)){
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, permission);
            }
            else{
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, permission);
            }
        }
        ListView songView = (ListView) findViewById(R.id.song_list);
        getSongList();
        SongAdapter songAdt = new SongAdapter(this, songList);
        songView.setAdapter(songAdt);
        setController();
    }
    @Override
    protected void onStart() {
        super.onStart();
        if(playIntent==null){
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    //connecting to music service
    private ServiceConnection musicConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicBinder binder = (MusicBinder)service;
            //geting service
            musicSrv = binder.getService();
            //passing songlist
            musicSrv.setList(songList);
            musicBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };
    //local
    public void getSongList() {
        songList = new ArrayList<Song>();   //local song list
        songListFire = new ArrayList<Song>();   //firebase song list
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);
        //adding songs to list
        if (musicCursor != null && musicCursor.moveToFirst()) {
            int titleColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.TITLE);
            int idColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media._ID);
            int artistColumn = musicCursor.getColumnIndex
                    (MediaStore.Audio.Media.ARTIST);
            do {
                long thisId = musicCursor.getLong(idColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);
                songList.add(new Song(thisId, thisTitle, thisArtist));
            }
            while (musicCursor.moveToNext());
        }
        StorageReference listRef = firebaseStorage.getReference();
        System.out.println("Inside storage");
        listRef.listAll().addOnSuccessListener(new OnSuccessListener<ListResult>() {
            @Override
            public void onSuccess(ListResult listResult) {
                int index = 0;
                for (final StorageReference item : listResult.getItems()) {
                    final String name = item.getName().split("-")[1].
                            substring(0, item.getName().split("-")[1].length() - 4);
                    System.out.println(name);
                    //songListFire.add(new Song(index,name,item.getName().split("-")[0]));
                    final int finalIndex = index;
                    item.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                        @Override
                        public void onSuccess(Uri uri) {
                            System.out.println(uri.toString());
                            musicSrv.uriList.add(uri.toString());
                            songListFire.add(new Song(finalIndex,name,item.getName().split("-")[0]));
                        }
                    });
                    index+=3;
                    System.out.println("Inside item");
                }
            }
        });
    }
    //Playing picked song
    public void songPicked(View view){
        int pos = Integer.parseInt(view.getTag().toString());
        musicSrv.setSong(pos);
        musicSrv.playSong();
        TextView playing = findViewById(R.id.playing);
        playing.setText("Playing: "+musicSrv.getSongTitle());
        if(playbackPaused){
            setController();
            playbackPaused=false;
        }
        controller.show(12000000);
    }
    @Override
    protected void onDestroy() {
        stopService(playIntent);
        musicSrv=null;
        super.onDestroy();
    }
    //Setting music controller
    private void setController(){
        controller = new MusicController(this);
        controller.setPrevNextListeners(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNext();
            }
        }, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPrev();
            }
        });
        controller.setMediaPlayer(this);
        controller.setAnchorView(findViewById(R.id.song_list));
        controller.setEnabled(true);
    }

    //Pause function
    @Override
    public void pause() {
        playbackPaused=true;
        musicSrv.pausePlayer();
        TextView playing = findViewById(R.id.playing);
        playing.setText("Paused: "+musicSrv.getSongTitle());
    }
    //Shuffle function
    public void shuffle(View view) {
        musicSrv.setShuffle();
    }
    //Stop function
    public void stop(View view)
    {
        musicSrv.onStop();
        TextView playing = findViewById(R.id.playing);
        playing.setText("");
    }
    //Skip function
    @Override
    public void seekTo(int pos) {
        musicSrv.seek(pos);
    }

    @Override
    public void start() {
        musicSrv.go();
    }

    @Override
    public int getDuration() {
        if(musicSrv!=null && musicBound && musicSrv.isPng())
            return musicSrv.getDur();
        else return 0;
    }

    @Override
    public int getCurrentPosition() {
        if(musicSrv!=null && musicBound && musicSrv.isPng())
            return musicSrv.getPosn();
        else return 0;
    }

    @Override
    public boolean isPlaying() {
        if(musicSrv!=null && musicBound)
            return musicSrv.isPng();
        return false;
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }
    //play next
    private void playNext(){
        musicSrv.playNext();
        TextView playing = findViewById(R.id.playing);
        playing.setText("Playing: "+musicSrv.getSongTitle());
        if(playbackPaused){
            setController();
            playbackPaused=false;
        }
        controller.show(0);
    }
    //play previous
    private void playPrev(){
        musicSrv.playPrev();
        TextView playing = findViewById(R.id.playing);
        playing.setText("Playing: "+musicSrv.getSongTitle());
        if(playbackPaused){
            setController();
            playbackPaused=false;
        }
        controller.show(0);
    }
    //switch between local and firebase songlists
    public void makeSwitch(View view)
    {
        ListView songView = (ListView) findViewById(R.id.song_list);
        Button button = findViewById (R.id.local);
        SongAdapter songAdt;
        musicSrv.local=!musicSrv.local;
        local=!local;
        if (local==false){
            songAdt = new SongAdapter(this, songListFire);
            System.out.println("Switched Online");
            musicSrv.setList(songListFire);
            button.setText("Online Server");
        }
        else
        {
            songAdt = new SongAdapter(this, songList);
            System.out.println("Switched Offline");
            musicSrv.setList(songList);
            button.setText("Local Server");
        }
        songView.setAdapter(songAdt);
        songView.invalidate();
        button.invalidate();
    }
}
