/**
 * 나가는 어댑터 · 포스터·로고 파일. 개발 중엔 로컬 저장, 업체는 배포 때 정한다 (docs/16).
 * <p>
 * {@link com.ticketon.storage.LocalFileStore}가 로컬 디스크에 묶음(파일 종류)별로 파일을 두고, 파일 종류마다 나가는 포트를 구현하는 어댑터가
 * 이 저장소를 함께 쓴다(주최자 로고: {@link com.ticketon.storage.LocalOrganizerLogoStorageAdapter}). 포스터 등 다른 파일은 묶음 이름만 달리한
 * 어댑터를 더한다.
 */
package com.ticketon.storage;
