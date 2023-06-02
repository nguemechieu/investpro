package org.investpro;

//+------------------------------------------------------------------+
//|   ENUM_CHAT_ACTION                                               |
//+------------------------------------------------------------------+
public enum ENUM_CHAT_ACTION {
    typing(1),// for text messages,
    upload_photo(2),//for photos,
    record_video(3),// or upload_video for videos,
    record_voice(4),//or upload_voice for voice notes,
    upload_document(5),//for general files,

    choose_sticker(6), //for stickers, find_location for location data,
    record_video_note(7), upload_audio(8); //or upload_video_note for video notes.

    ENUM_CHAT_ACTION(int i) {
    }
}
