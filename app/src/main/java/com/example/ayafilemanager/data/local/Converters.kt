package com.example.ayafilemanager.data.local

import androidx.room.TypeConverter
import com.example.ayafilemanager.domain.model.FileType

class Converters {
    @TypeConverter fun typeToString(value: FileType): String = value.name
    @TypeConverter fun stringToType(value: String): FileType = FileType.valueOf(value)
}
