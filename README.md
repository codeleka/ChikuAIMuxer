# ChikuAIMuxer
ChikuAI Muxer 

* Audio + Video Joiner
* URI to File Path
* Get File Duration // Audio or Video
* Get File Size
* File Chooser


  Easy to replace audio of any video


  Audio Support AAC
  Video Support MP4



  Calling Methods ====================================

------Mix Audio Inside Video


      ProgressDialog loader = new ProgressDialog(MainActivity.this);
      loader.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
      loader.setTitle("Please Wait..");
      loader.setButton("Cancel", (dialogInterface, i) -> {
          ChikuMuxer.cancelMixer = true;
          loader.dismiss();
      });
      loader.setCancelable(false);
      loader.setCanceledOnTouchOutside(false);
        
      ChikuMuxer.videoAudioMuxer("Your Video Path", "Your Audio Path",YourActivity.this, new ChikuMuxer.ChikuMux() {
            @Override
              public void onStart() {
                // call back on start
                loader.setMax(total_size);
                loader.show();
            }
            @Override
            public void onProgress(int length, int progress) {
                // call back on Progress of Mixing
                loader.setProgress(progress);
            }

            @Override
            public void onComplete(String path) {
                // call back on complete with saved video path
                loader.dismiss();
            }

            @Override
            public void onFailed(String message) {
                // call back on error , file missing , permission missing as well type 
                loader.dismiss();
            }
       });


---------- Uri to Path 

    String filePath = ChikuMuxer.getPathFromUri(YourActivity.this, uri);



---------- File Path to Duration 

    double file_dur = ChikuMuxer.getDurationInt(String.valueOf(file));



----------- File Path to size 


    int file_size = ChikuMuxer.getSize(file);
    
    int file_size_kb = ChikuMuxer.getSizeInKb(file);


------------ Chooser 

    ChikuMuxer.chooser(YourActivity.this, "FileType/FileType",RESPONSE_CODE); // e.g. ChikuMuxer.chooser(MainActivity.this, "video/*",101); 


