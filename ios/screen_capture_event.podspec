#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint screen_capture_event.podspec` to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'screen_capture_event'
  s.version          = '0.0.1'
  s.summary          = 'Catch screen capture (Screenshot & Screen Record) event.'
  s.description      = <<-DESC
Catch screen capture (Screenshot & Screen Record) event for Android and iOS.
                       DESC
  s.homepage         = 'https://github.com/nizwar/screen_capture_event'
  s.license          = { :type => 'MIT', :file => '../LICENSE' }
  s.author           = { 'Your Company' => 'email@example.com' }
  s.source           = { :path => '.' }
  s.source_files = 'screen_capture_event/Sources/screen_capture_event/**/*.swift'
  s.dependency 'Flutter'
  s.platform = :ios, '15.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'
end
