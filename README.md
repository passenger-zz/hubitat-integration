# hubitat-integration

In dashboard just use the tileHTML attribute to show the data in tail.
Require som CSS in the dashboard to render nicely and show icons:



@@import "https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined";

.custom {
   width: 100%;
}
.custom caption span {
   font-size: 40px;
}
.custom tr th, 
.custom tr td {
   padding: 0 0.5em;
   font-size: small;
   opacity: 50%;
}
.custom tr th {
   text-align: right;
    width: 33%;
}
.custom tr.head th {
   font-size: large;
   opacity: 100%;
   text-align: center;
   width: 33%;
}
.custom tr td {
   text-align: left;
}
