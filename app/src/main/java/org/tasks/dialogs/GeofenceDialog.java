package org.tasks.dialogs;

import static android.app.Activity.RESULT_OK;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.tasks.R;
import org.tasks.data.LocationExtensionsKt;
import org.tasks.data.entity.Geofence;
import org.tasks.data.Location;
import org.tasks.databinding.LocationDetailsBinding;

import dagger.hilt.android.AndroidEntryPoint;

import javax.inject.Inject;

@AndroidEntryPoint
public class GeofenceDialog extends DialogFragment {

  public static final String EXTRA_GEOFENCE = "extra_geofence";
  private static final String EXTRA_ORIGINAL = "extra_original";

  @Inject DialogBuilder dialogBuilder;
  @Inject Activity context;

  private SwitchMaterial departureView;
  private Geofence originalGeofence;

  public static GeofenceDialog newGeofenceDialog(Location location) {
    GeofenceDialog dialog = new GeofenceDialog();
    Bundle args = new Bundle();
    args.putParcelable(EXTRA_ORIGINAL, location);
    dialog.setArguments(args);
    return dialog;
  }

  @NonNull
  @Override
  public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
    Location original = getArguments().getParcelable(EXTRA_ORIGINAL);
    originalGeofence = original.getGeofence();
    Geofence geofence =
        savedInstanceState == null
            ? originalGeofence
            : savedInstanceState.getParcelable(EXTRA_GEOFENCE);

    LayoutInflater layoutInflater = LayoutInflater.from(context);
    LocationDetailsBinding binding = LocationDetailsBinding.inflate(layoutInflater);
    departureView = binding.locationDeparture;
    departureView.setChecked(geofence.isDeparture());
    return dialogBuilder
        .newDialog(LocationExtensionsKt.getDisplayName(original))
        .setView(binding.getRoot())
        .setNegativeButton(R.string.cancel, null)
        .setPositiveButton(R.string.ok, this::sendResult)
        .create();
  }

  private Geofence toGeofence() {
    boolean departure = departureView.isChecked();
    return new Geofence(
            originalGeofence.getId(),
            originalGeofence.getTask(),
            originalGeofence.getPlace(),
            !departure,
            departure
    );
  }

  private void sendResult(DialogInterface d, int... i) {
    Intent data = new Intent();
    data.putExtra(EXTRA_GEOFENCE, (Parcelable) toGeofence());
    getTargetFragment().onActivityResult(getTargetRequestCode(), RESULT_OK, data);
    dismiss();
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putParcelable(EXTRA_GEOFENCE, toGeofence());
  }
}
