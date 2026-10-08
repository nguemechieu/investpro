package org.investpro.utils;

public enum ENUM_CHAT_ACTION {


    //+------------------------------------------------------------------+
//|   ENUM_CHAT_ACTION                                               |
//+------------------------------------------------------------------+

    typing(),// for text messages,
    upload_photo(),//for photos,
    record_video(),// or upload_video for videos,
    record_voice(),//or upload_voice for voice notes,
    upload_document(),//for general files,

    choose_sticker(), //for stickers, find_location for location data,
    record_video_note(), upload_audio(); //or upload_video_note for video notes.

    ENUM_CHAT_ACTION() {
    }
}

