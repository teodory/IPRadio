package com.example.ipradio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.example.ipradio.ui.theme.IPRadioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
//            IPRadioTheme {
//                // A surface container using the 'background' color from the theme
//                Surface(
//                    modifier = Modifier.fillMaxSize(),
//                    color = MaterialTheme.colorScheme.background
//                ) {
//                    Greeting("Android")
//                }
//            }

//            val scrollState = rememberScrollState()
//            LazyColumn {
//                    items(80)
//                    {
//                        Text(text = "Item $it")
//                    }
//                }
//            PlayCard(name = "AAAA")


//            ConstraintLayoutContent()
//            Test2()
            Test3()

//            Box(modifier = Modifier.height(150.dp).wrapContentHeight(Alignment.Bottom))
//            {
//                Text(text = "kyp")
//            }
        }
    }
}

@Composable
fun Test3() {
    ConstraintLayout {
        val (box1, box2, button1, button2, spacer) = createRefs()

        Column (
            modifier = Modifier
                .background(Color.Gray)
                .fillMaxSize()
        ){

            Column (
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Bottom
            ) {

                Card (modifier = Modifier
                    .fillMaxSize()
                    .height(120.dp)
                    .fillMaxWidth()
                    .background(Color.Red)
                ) {

                    Column {
                        Box(modifier = Modifier
                            .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "asdfsadasd")
                        }
                        Row (modifier = Modifier
                            .border(1.dp, Color.Yellow)
                            .fillMaxWidth()
                        ){
                            ConstraintLayout {

                                Button(onClick = { /*TODO*/ },
                                    modifier = Modifier.constrainAs(button1)
                                    {
                                        start.linkTo(parent.start)
                                    }
                                ) {
                                    Text(text = "<<")
                                }

                                Button(onClick = { /*TODO*/ },
                                    modifier = Modifier.constrainAs(button2)
                                    {
                                        start.linkTo(button1.end)
                                    }
                                ) {
                                    Text(text = "Play")
                                }

                                Spacer(modifier = Modifier.constrainAs(spacer){
                                    start.linkTo(button2.end)
                                }.width(100.dp))

                                Box(modifier = Modifier
                                    .constrainAs(box2)
                                    {
                                        start.linkTo(spacer.end)
                                        end.linkTo(parent.end)
                                    }
                                    .width(80.dp)
                                    .height(70.dp)
                                    .border(2.dp, Color.Magenta)
                                    .padding(5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "Image", color = Color.Magenta)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun Test2() {
    Column (modifier = Modifier
        .background(Color.Yellow)
        .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Bottom
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Card (
                modifier = Modifier
                    .background(Color.Blue)
                    .fillMaxWidth()
                    .height(150.dp)
                    .border(2.dp, Color.Red)
            ) {
                Column {
                    Box(modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(2.dp))
                    {
                        Text(text = "Title")
//                        Text(text = "Second")
                    }

                        Row(modifier = Modifier.padding()) {

                            Button(
                                onClick = { /*TODO*/ },
                                modifier = Modifier
//                                .border(2.dp, Color.Green)
                                    .padding(5.dp)
                            ) {
                                Text(text = "<<")
                            }
                            Button(
                                onClick = { /*TODO*/ },
                                modifier = Modifier
                                    .padding(5.dp)
                            ) {
                                Text(text = "Play")
                            }
                            Button(
                                onClick = { /*TODO*/ },
                                modifier = Modifier
                                    .padding(5.dp)
                            ) {
                                Text(text = ">>")
                            }
                            Spacer(modifier = Modifier.weight(0.2f))
                            Box(
                                modifier = Modifier
                                    .border(2.dp, color = Color.Red)
                                    .height(85.dp)
                                    .width(85.dp),
                                contentAlignment = Alignment.Center

                            ) {
                                Text(text = "Image")
                            }

                        }
                    Box(modifier = Modifier
                        .padding(5.dp)
                        .border(1.dp, color = Color.Cyan))
                    {
                        Text(text = "Song info00000")
                    }
//                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
fun Test1() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(all = 10.dp)
                .background(Color.Red)
        ) {
            Text(text = "Card 1", modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .align(Alignment.CenterHorizontally))
        }
    }
}


@Composable
fun ConstraintLayoutContent() {

    Column (
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(bottom = 15.dp, start = 16.dp, end = 16.dp)
    )
    {
        ConstraintLayout {

            val (list, box, spacer) = createRefs()
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.constrainAs(list)
            {
                top.linkTo(parent.top)
            })
            {
                LazyColumn {
                    items(40)
                    {
                        Text(text = "Item $it")
                    }
                }
            }
            Spacer(modifier = Modifier)
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Bottom
            ) {
                Card(
                    modifier = Modifier
                        .height(200.dp)
                        .background(color = Color.Gray)
                        .fillMaxWidth()
                        .background(Color.Magenta)
//                    .constrainAs(box)
//                    {
////                        top.linkTo(spacer.bottom, margin = 16.dp)
//                        bottom.linkTo(parent.bottom, margin = 16.dp)
//                    }
                        .wrapContentHeight(Alignment.Bottom)
//            contentAlignment = Alignment.BottomCenter
                )
                {
                    Text(text = "asfa")
                }
            }
        }
    }
}

@Composable
fun PlayCard(name:String, modifier: Modifier = Modifier)
{
    Column {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .align(Alignment.CenterHorizontally)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Red)
                .width(150.dp)
                .padding(16.dp)
//                    .align(Alignment.CenterHorizontally)
        )
        {
            Box(
                modifier = modifier
                    .height(120.dp)
                    .background(color = Color.Gray)
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
//            contentAlignment = Alignment.BottomCenter
            )
            {
                Text(
                    text = name, modifier = Modifier
                        .align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Greeting("Android")
}