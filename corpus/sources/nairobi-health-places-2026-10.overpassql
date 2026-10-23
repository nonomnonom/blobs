[out:json][timeout:60];
// Named health places inside the Nairobi central-business-area bounding box.
// Bounded on purpose: one reviewed lock pins one small snapshot, and the capture
// rejects a response over 1,000 elements or 5,000,000 bytes.
(
  node["amenity"~"^(pharmacy|hospital|clinic)$"](-1.300,36.800,-1.240,36.860);
  way["amenity"~"^(pharmacy|hospital|clinic)$"](-1.300,36.800,-1.240,36.860);
);
out center tags;
