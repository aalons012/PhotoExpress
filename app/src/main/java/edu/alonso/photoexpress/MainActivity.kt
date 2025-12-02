// Made By Andy Alonso
// COP 2662
// 12/2/25

package edu.alonso.photoexpress

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LightingColorFilter
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts.TakePicture
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // A File object to hold the image file captured by the camera. Null until a photo is taken.
    private var photoFile: File? = null

    // UI elements that will be manipulated in the code.
    private lateinit var photoImageView: ImageView
    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var saveButton: Button

    // Variables for applying a brightness filter to the image.
    // multColor is used for multiplicative color scaling (for darkening).
    private var multColor = -0x1
    // addColor is used for additive color shifting (for brightening).
    private var addColor = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components by finding them in the layout.
        photoImageView = findViewById(R.id.photo)

        saveButton = findViewById(R.id.save_button)
        saveButton.setOnClickListener { savePhotoClick() }
        // Disable the save button initially, as there's no photo to save.
        saveButton.isEnabled = false

        // Set up the button to launch the camera.
        findViewById<Button>(R.id.take_photo_button).setOnClickListener { takePhotoClick() }

        // Initialize the brightness SeekBar and hide it until a photo is taken.
        brightnessSeekBar = findViewById(R.id.brightness_seek_bar)
        brightnessSeekBar.visibility = View.INVISIBLE

        // Add a listener to the SeekBar to detect when the user changes the brightness.
        brightnessSeekBar.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                // Update the image brightness whenever the progress value changes.
                changeBrightness(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {} // Not used
            override fun onStopTrackingTouch(seekBar: SeekBar) {}  // Not used
        })
    }

    /**
     * Called when the "Take Photo" button is clicked.
     * It creates a file and launches the camera app.
     */
    private fun takePhotoClick() {
        // Create a file with a unique name to store the new photo.
        photoFile = createImageFile()

        // Create a content URI using a FileProvider. This is required for security on Android 7.0+
        // to grant the camera app temporary write access to our app's private file.
        val photoUri = FileProvider.getUriForFile(
            this, "edu.alonso.photoexpress.fileprovider", photoFile!!)

        // Launch the external camera app, passing it the URI where it should save the photo.
        takePicture.launch(photoUri)
    }

    /**
     * This is the modern way to handle activity results.
     * It registers a callback for the result of the `TakePicture` contract.
     */
    private val takePicture = registerForActivityResult(TakePicture()) { success ->
        // This lambda block is executed when the camera app returns.
        // 'success' is true if the photo was successfully captured and saved to the provided URI.
        if (success) {
            // If the photo was taken, display it in the ImageView.
            displayPhoto()
            // Make the brightness SeekBar visible and set its initial progress.
            brightnessSeekBar.progress = 100 // 100 represents normal brightness
            brightnessSeekBar.visibility = View.VISIBLE
            // Apply the initial brightness filter.
            changeBrightness(brightnessSeekBar.progress)
            // Enable the save button now that there's an image.
            saveButton.isEnabled = true
        }
    }

    /**
     * Creates a unique File in the app's private external storage directory.
     * @return A File object representing the new image file.
     */
    private fun createImageFile(): File {
        // Create a unique image filename using a timestamp to avoid collisions.
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val imageFilename = "photo_$timeStamp.jpg"

        // Get the directory where the app can save private images.
        // These files are private to the app but stored on external storage.
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        // Create and return the File object.
        return File(storageDir, imageFilename)
    }

    /**
     * Decodes the image from the file and displays it in the ImageView.
     * This method also scales the image down to prevent `OutOfMemoryError` and fit the view.
     */
    private fun displayPhoto() {
        // Get the dimensions of the target ImageView.
        val targetWidth = photoImageView.width
        val targetHeight = photoImageView.height

        // First, decode with inJustDecodeBounds=true to check dimensions without loading the image into memory.
        val bmOptions = BitmapFactory.Options()
        bmOptions.inJustDecodeBounds = true
        BitmapFactory.decodeFile(photoFile!!.absolutePath, bmOptions)
        val photoWidth = bmOptions.outWidth
        val photoHeight = bmOptions.outHeight

        // Calculate the `inSampleSize` to scale down the image.
        // This value must be a power of 2.
        val scaleFactor = Math.min(photoWidth / targetWidth, photoHeight / targetHeight)

        // Now, decode the image file into a Bitmap, applying the scale factor.
        bmOptions.inJustDecodeBounds = false
        bmOptions.inSampleSize = scaleFactor
        val bitmap = BitmapFactory.decodeFile(photoFile!!.absolutePath, bmOptions)

        // Set the scaled bitmap on the ImageView.
        photoImageView.setImageBitmap(bitmap)
    }

    /**
     * Applies a brightness effect to the photoImageView using a LightingColorFilter.
     * @param brightness An integer from 0 to 200, where 100 is normal brightness.
     */
    private fun changeBrightness(brightness: Int) {
        // 100 is the neutral middle value from the SeekBar.
        if (brightness > 100) {
            // Brighten the image: Use an additive color.
            val addMult = brightness / 100f - 1
            addColor = Color.argb(
                255, (255 * addMult).toInt(), (255 * addMult).toInt(),
                (255 * addMult).toInt()
            )
            // Set multColor to neutral (-1 or 0xFFFFFFFF means no multiplication).
            multColor = -0x1
        } else {
            // Darken the image: Use a multiplicative color.
            val brightMult = brightness / 100f
            multColor = Color.argb(
                255, (255 * brightMult).toInt(), (255 * brightMult).toInt(),
                (255 * brightMult).toInt()
            )
            // Set addColor to neutral (0 means no color is added).
            addColor = 0
        }

        // Create and apply the color filter to the ImageView.
        val colorFilter = LightingColorFilter(multColor, addColor)
        photoImageView.colorFilter = colorFilter
    }

    /**
     * Called when the "Save" button is clicked.
     * It saves the currently displayed photo with the applied brightness filter.
     */
    private fun savePhotoClick() {
        // Disable the button to prevent multiple clicks while saving.
        saveButton.isEnabled = false

        // Ensure photoFile is not null before proceeding.
        if (photoFile != null) {
            // Launch a coroutine on the Main thread to handle the save operation.
            CoroutineScope(Dispatchers.Main).launch {
                // Call the suspend function to perform the file I/O in a background thread.
                saveAlteredPhoto(photoFile!!, multColor, addColor)

                // After saving, show a confirmation message to the user on the Main thread.
                Toast.makeText(applicationContext, R.string.photo_saved, Toast.LENGTH_LONG).show()

                // Re-enable the Save button.
                saveButton.isEnabled = true
            }
        }
    }

    /**
     * A suspend function that saves the photo with the filter applied.
     * `withContext(Dispatchers.IO)` switches to a background thread optimal for I/O operations.
     * @param photoFile The original photo file to read.
     * @param filterMultColor The multiplicative color for the filter.
     * @param filterAddColor The additive color for the filter.
     */
    private suspend fun saveAlteredPhoto(photoFile: File, filterMultColor: Int, filterAddColor: Int) = withContext(Dispatchers.IO) {
        // Read the original image file into a Bitmap.
        val origBitmap = BitmapFactory.decodeFile(photoFile.absolutePath, null)

        // Create a new mutable Bitmap with the same dimensions as the original.
        // Use the elvis operator `?:` to provide a default config if the original's is null.
        val alteredBitmap = Bitmap.createBitmap(origBitmap.width, origBitmap.height,
            origBitmap.config ?: Bitmap.Config.ARGB_8888)

        // Prepare to draw on the new bitmap.
        val canvas = Canvas(alteredBitmap)
        val paint = Paint()
        val colorFilter = LightingColorFilter(filterMultColor, filterAddColor)
        paint.colorFilter = colorFilter

        // Draw the original bitmap onto the new canvas, applying the color filter via the paint object.
        canvas.drawBitmap(origBitmap, 0f, 0f, paint)

        // ---- Save the altered bitmap to the public MediaStore ----

        // Create a ContentValues object to hold the image's metadata for the MediaStore.
        val imageValues = ContentValues()
        imageValues.put(MediaStore.MediaColumns.DISPLAY_NAME, photoFile.name)
        imageValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")

        // For Android 10 (API 29) and above, use RELATIVE_PATH to specify the save location (Pictures directory).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            imageValues.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
        }

        // Get the ContentResolver and insert a new entry into the MediaStore, which returns a URI.
        val resolver = this@MainActivity.applicationContext.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, imageValues)

        // Use the returned URI to open an output stream and save the bitmap.
        uri?.let {
            runCatching {
                // Safely open an output stream. The `.use` block will only execute if it's not null,
                // and it will automatically close the stream afterward.
                resolver.openOutputStream(it)?.use { outStream ->
                    // Compress the bitmap into JPEG format and write it to the output stream.
                    alteredBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outStream)
                }
            }
        }
    }
}
