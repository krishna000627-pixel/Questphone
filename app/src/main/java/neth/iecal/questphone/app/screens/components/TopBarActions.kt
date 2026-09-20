package neth.iecal.questphone.app.screens.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import neth.iecal.questphone.R
import neth.iecal.questphone.app.theme.LocalCustomTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarActions(
    coins: Int,
    streak: Int,
    isCoinsVisible: Boolean = false,
    isStreakVisible: Boolean = false,
    onCoinsClick: (() -> Unit)? = null,
    onStreakClick: (() -> Unit)? = null,
    onFocusClick: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onFocusClick != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = LocalCustomTheme.current.getExtraColorScheme().toolBoxContainer.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable(
                        onClick = onFocusClick,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "⚡ Focus",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isCoinsVisible) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = LocalCustomTheme.current.getExtraColorScheme().toolBoxContainer,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable(
                        enabled = onCoinsClick != null,
                        onClick = { onCoinsClick?.invoke() },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.coin_icon),
                    contentDescription = "Coins",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = coins.toString(),
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isStreakVisible) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = LocalCustomTheme.current.getExtraColorScheme().toolBoxContainer,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable(
                        enabled = onStreakClick != null,
                        onClick = { onStreakClick?.invoke() },
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.streak),
                    contentDescription = "Streak",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.size(4.dp))
                Text(
                    text = streak.toString(),
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.size(8.dp))
    }
}