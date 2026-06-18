package com.mahdi.musicpro.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.unit.sp
import com.mahdi.musicpro.R

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_light, FontWeight.Light, loadingStrategy = FontLoadingStrategy.OptionalLocal),
    Font(R.font.vazirmatn_regular, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.OptionalLocal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium, loadingStrategy = FontLoadingStrategy.OptionalLocal),
    Font(R.font.vazirmatn_medium, FontWeight.SemiBold, loadingStrategy = FontLoadingStrategy.OptionalLocal),
    Font(R.font.vazirmatn_bold, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.OptionalLocal)
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Vazirmatn,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
)
