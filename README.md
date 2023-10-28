# hubitat-integration

In dashboard just use the tileHTML attribute to show the data in tail.
Require som CSS in the dashboard to render nicely and show icons:



@import "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined";
.tile.power .tile-contents .tile-primary:after { content: " W"; font-size: 50%; }
.tile.energy .tile-contents .tile-primary:after { content: " kWh"; font-size: 50%; }
.SolarInverter {
   width: 100%;
}
.SolarInverter caption span {
   font-size: 40px
}
.tibber {
   width: 100%;
}
.tibber caption span {
   font-size: 40px
}
