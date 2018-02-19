# URL
URL parser for java without temporary string objects.

This is useful when you are required to parse lots of URL and decrease pressure on GC.
Parsed parts are presented as CharSequence's.


        URL url = new URL("http://domain.com/some-path?user=jane&user=john#frag");
        
        CharSequence scheme = url.getScheme();
        if (scheme.equals("http")) {
            System.out.println("Scheme is http");
        }
        
        Map<CharSequence, URL.QueryItem> items = url.getQueryItems();
        URL.QueryItem values = items.get("user");
        while (values != null) {
            System.out.println("user : " + values.value);
            values = values.next;
        }
