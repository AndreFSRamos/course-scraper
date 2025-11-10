package tech.andrefsramos.course_scraper.core.domain;

public final class CourseChangePolicy {
    public static boolean isRelevantUpdate(Course oldC, Course newC) {
        if (oldC == null) return true;
        boolean statusChanged = diff(oldC.statusText(), newC.statusText());
        boolean priceChanged  = diff(oldC.priceText(), newC.priceText());
        boolean datesChanged  = oldC.startDate() != newC.startDate() || oldC.endDate() != newC.endDate();
        return statusChanged || priceChanged || datesChanged;
    }
    private static boolean diff(String a, String b) {
        return (a == null && b != null) || (a != null && !a.equals(b));
    }
}
