<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<script src="http://ajax.googleapis.com/ajax/libs/jquery/1.8.0/jquery.min.js" type="text/javascript"></script>
<script src="http://maps.google.com/maps/api/js?sensor=true" type="text/javascript"></script>
<script src="gmaps.js" type="text/javascript"></script>
<link rel="stylesheet" href="http://twitter.github.com/bootstrap/1.3.0/bootstrap.min.css" />
<link rel="stylesheet" type="text/css" href="gmaps.css" />
</head>
<body>
<script type="text/javascript">
$(document).ready(function() {

// Shortcut get JSON & add to javascript var
<?php $message = file_get_contents('http://martinpoulsen.pythonanywhere.com/positions/json/get_all_locations/'); ?>
var json = <?php echo $message; ?>;

// Make new array for GMAPS
gmapscoordinates = new Array (10000);
var map;
var map2;

// Iterate json array
for(var i = 0; i < json.length; i++)
{
    if(json[i].positions.length > 0)
    {
    	
    	// Populate new array for GMAPS
    	gmapscoordinates [i] = new Array (10000);
    	
    	// Iterate positions
        for(var j = 0; j < json[i].positions.length; j++)
        {
			
        	gmapscoordinates [i] [j] = new Array (10000);
        	
        	// Populate GMAPS array
        	for (k = 0; k < gmapscoordinates. length; ++ k) {
        		
        		
        		
        		gmapscoordinates [i] [j] [0] = json[i].positions[j].lat;
        		gmapscoordinates [i] [j] [1] = json[i].positions[j].long;
        		
        	}
        }
    }
}


map = new GMaps({
    el: '#map',
    lat: 55.6599177,
    lng: 12.5902543,
    click: function(e){
      console.log(e);
    }
  });
  
//map 1

  map.drawPolyline({
    path: gmapscoordinates[0],
    strokeColor: '#131540',
    strokeOpacity: 0.6,
    strokeWeight: 6
  });
  
//map 2
  
  map.drawPolyline({
	    path: gmapscoordinates[1],
	    strokeColor: '#ff0000',
	    strokeOpacity: 0.6,
	    strokeWeight: 6
	  });

});


</script>

<center><div id="map"></div></center>


</body>
</html>