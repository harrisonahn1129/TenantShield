package com.example.tenantshield.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// TODO: Replace with Manrope (headlines) and Inter (body) via Google Fonts
val HeadlineFont = FontFamily.Default
val BodyFont = FontFamily.Default

val Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    titleLarge = TextStyle(
        fontFamily = HeadlineFont,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp
    ),
    titleSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        letterSpacing = 1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFont,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp
    ),
)
