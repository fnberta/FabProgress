# FabProgress
Simple libarary that implements the Floating Action Button circle loader pattern. Uses the FAB implementation from Google design support lib.

Uses the circle animations from Jorge Castillo Pérez (https://github.com/JorgeCastilloPrz/FABProgressCircle). But instead of using a ViewGroup, subclasses the Google design support FAB.

# Usage
NOTE: I created this lib for usage in one of my projects. It is very limited and focused on what I needed in the project. Use at your own risk!

Add to your `build.gradle`:
```groovy
repositories {
  // ...
  maven { url "https://jitpack.io" }
}

dependencies {
  compile 'com.github.fnberta:FabProgress:0.1'
}
```

Check the sample project for further info on how to use it. If you think something is missing, let me know and I will try to add it.
