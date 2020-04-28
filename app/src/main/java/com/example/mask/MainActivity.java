package com.example.mask;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.PointF;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.naver.maps.geometry.LatLng;
import com.naver.maps.map.MapFragment;
import com.naver.maps.map.NaverMap;
import com.naver.maps.map.OnMapReadyCallback;
import com.naver.maps.map.UiSettings;
import com.naver.maps.map.overlay.InfoWindow;
import com.naver.maps.map.overlay.Marker;
import com.naver.maps.map.overlay.Overlay;
import com.naver.maps.map.overlay.OverlayImage;
import com.naver.maps.map.util.FusedLocationSource;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, NaverMap.OnCameraIdleListener, NaverMap.OnCameraChangeListener, Overlay.OnClickListener, NaverMap.OnMapClickListener {

    private static final int ACCESS_LOCATION_PERMISSION_REQUEST_CODE =100;
    private FusedLocationSource locationSource;
    private NaverMap naverMap;
    private InfoWindow infoWindow;
    private List<Marker> markerList=new ArrayList<Marker>();
    private boolean isCameraAnimated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MapFragment mapFragment=(MapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull NaverMap naverMap) {
        this.naverMap=naverMap;

        locationSource =new FusedLocationSource(this,ACCESS_LOCATION_PERMISSION_REQUEST_CODE);
        naverMap.setLocationSource(locationSource);
        UiSettings uiSettings=naverMap.getUiSettings();

        //현재위치 버튼 활성화
        uiSettings.setLocationButtonEnabled(true);

        naverMap.addOnCameraChangeListener(this);
        //지도에서 이동이 멈췄을 떄 알리는 리스너
        naverMap.addOnCameraIdleListener(this);
        naverMap.setOnMapClickListener(this);

        infoWindow=new InfoWindow();

        infoWindow.setAdapter(new InfoWindow.DefaultViewAdapter(this) {
            @NonNull
            @Override
            protected View getContentView(@NonNull InfoWindow infoWindow) {
                Marker marker = infoWindow.getMarker();
                Store store = (Store) marker.getTag();
                View view = View.inflate(MainActivity.this, R.layout.view_info_window, null);
                ((TextView) view.findViewById(R.id.name)).setText(store.name);
                if ("plenty".equalsIgnoreCase(store.remain_stat)) {
                    ((TextView) view.findViewById(R.id.stock)).setText("100개 이상");
                } else if ("some".equalsIgnoreCase(store.remain_stat)) {
                    ((TextView) view.findViewById(R.id.stock)).setText("30개 이상 100개 미만");
                } else if ("few".equalsIgnoreCase(store.remain_stat)) {
                    ((TextView) view.findViewById(R.id.stock)).setText("2개 이상 30개 미만");
                } else if ("empty".equalsIgnoreCase(store.remain_stat)) {
                    ((TextView) view.findViewById(R.id.stock)).setText("1개 이하");
                } else if ("break".equalsIgnoreCase(store.remain_stat)) {
                    ((TextView) view.findViewById(R.id.stock)).setText("판매중지");
                } else {
                    ((TextView) view.findViewById(R.id.stock)).setText(null);
                }
                ((TextView) view.findViewById(R.id.time)).setText("입고 " + store.stock_at);
                return view;
            }
        });

        //카메라 중심 위치
        LatLng mapCenter=naverMap.getCameraPosition().target;
        fetchStoreSale(mapCenter.latitude, mapCenter.longitude, 5000);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode){
            case ACCESS_LOCATION_PERMISSION_REQUEST_CODE:
                locationSource.onRequestPermissionsResult(requestCode,permissions,grantResults);
                return;
        }


    }

    private void fetchStoreSale(double lat, double lng, int m){
        //베이스 Url에서 JSON 데이터를 GSON 형태로 변환
        Retrofit retrofit=new Retrofit.Builder().baseUrl("https://8oi9s0nnth.apigw.ntruss.com").addConverterFactory(GsonConverterFactory.create()).build();
        //해당 인터페이스를 갖는 자바 객체
        MaskApi maskApi=retrofit.create(MaskApi.class);

        maskApi.getStoresByGeo(lat,lng,m).enqueue(new Callback<StoreSaleResult>() {
            @Override
            public void onResponse(Call<StoreSaleResult> call, Response<StoreSaleResult> response) {

                //200 Http 리스폰이 OK인 경우
                if(response.code() ==200){
                    StoreSaleResult result=response.body();
                    UpdateMapMarkers(result);
                }
            }

            @Override
            public void onFailure(Call<StoreSaleResult> call, Throwable t) {

            }
        });
    }

    private void UpdateMapMarkers(StoreSaleResult result){
        resetMarkerList();

        //약국 정보가 있다
        if(result.stores !=null && result.stores.size() >0){
            for(Store store : result.stores){
                Marker marker =new Marker();

                //스토어 정보 객체를 태킹
                marker.setTag(store);
                marker.setPosition(new LatLng(store.lat,store.lng));

                if("plenty".equalsIgnoreCase(store.remain_stat)){
                    marker.setIcon(OverlayImage.fromResource(R.drawable.marker_green));
                }else if("some".equalsIgnoreCase(store.remain_stat)){
                    marker.setIcon(OverlayImage.fromResource(R.drawable.marker_yellow));
                }else if("fiew".equalsIgnoreCase(store.remain_stat)){
                    marker.setIcon(OverlayImage.fromResource(R.drawable.marker_red));
                }else{
                    marker.setIcon(OverlayImage.fromResource(R.drawable.marker_gray));
                }

                //마커 아이콘 위치
                marker.setAnchor(new PointF(0.5f,1.0f));
                marker.setMap(naverMap);
                marker.setOnClickListener(this);
                markerList.add(marker);

            }
        }
    }

    private void resetMarkerList(){
        if(markerList !=null && markerList.size()>0){
            for(Marker marker :markerList){
                marker.setMap(null);
            }
            markerList.clear();
        }
    }

    @Override
    public void onCameraIdle() {

        //카메라 이동이 멈추고 한번만 호출
        if(isCameraAnimated){
            LatLng mapCenter=naverMap.getCameraPosition().target;
            fetchStoreSale(mapCenter.latitude,mapCenter.longitude,5000);
        }

    }

    @Override
    public void onCameraChange(int i, boolean b) {
        isCameraAnimated =b;
    }

    @Override
    public boolean onClick(@NonNull Overlay overlay) {

        if(overlay instanceof Marker) {
            Marker marker = (Marker) overlay;

            //이미 마커 인포윈도우가 떠 있을때
            if(marker.getInfoWindow() !=null) {
                infoWindow.close();
            }else {
                infoWindow.open(marker);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onMapClick(@NonNull PointF pointF, @NonNull LatLng latLng) {
        if(infoWindow.getMarker() !=null){
            infoWindow.close();
        }
    }
}
